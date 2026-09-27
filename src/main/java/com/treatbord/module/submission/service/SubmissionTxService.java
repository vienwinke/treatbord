package com.treatbord.module.submission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treatbord.common.AfterCommit;
import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.audit.service.AuditService;
import com.treatbord.module.notify.service.NotificationService;
import com.treatbord.module.submission.dto.SubmissionRequest;
import com.treatbord.module.submission.entity.TaskSubmission;
import com.treatbord.module.submission.mapper.TaskSubmissionMapper;
import com.treatbord.module.task.entity.Task;
import com.treatbord.module.task.entity.TaskClaim;
import com.treatbord.module.task.enums.ClaimStatus;
import com.treatbord.module.task.mapper.TaskClaimMapper;
import com.treatbord.module.task.mapper.TaskMapper;
import com.treatbord.module.task.service.ClaimService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 凭证提交的**事务内写入**（独立 Bean）。
 *
 * <p>为什么单独拆出来：`SubmissionService.submit()` 内含**内容安全检测（网络调用）**。
 * 若和数据库写入同处一个事务，事务会在等待 HTTP 响应期间一直占用数据库连接与行锁（长事务）。
 * 拆成「非事务编排（校验 + 外部调用） + 事务内写入」后，事务只剩几条 SQL 的执行时间。
 *
 * <p>⚠️ 必须由**另一个 Bean** 调用本方法，{@code @Transactional} 才会经代理生效；
 * 同类内部调用会绕过代理（Spring 事务经典失效场景）。
 */
@Service
@RequiredArgsConstructor
public class SubmissionTxService {

    private static final long REVIEW_TIMEOUT_HOURS = 48;

    /** 可提交凭证的任务状态（跨聚合校验用） */
    private static final Set<String> SUBMITTABLE_TASK_STATUSES =
            Set.of("OPEN", "IN_PROGRESS", "REVIEWING");

    private final TaskSubmissionMapper submissionMapper;
    private final TaskClaimMapper taskClaimMapper;
    private final TaskMapper taskMapper;
    private final ClaimService claimService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final com.treatbord.module.task.service.TaskCacheService taskCacheService;

    /**
     * 事务内写入凭证：权威校验 → 覆盖/新建 submission → CAS 流转 → 审核窗口 → 状态留痕 → 提交后审计与通知。
     *
     * <p>事务外已预校验过一次，这里**重读并再校验**，是为了关闭"预校验之后状态被并发修改"的窗口；
     * 状态流转仍走 CAS（返回 0 行即抛 3003）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void persistSubmission(Long claimId, Long userId, SubmissionRequest req, HttpServletRequest httpReq) {
        // 1. 权威校验（事务内）
        TaskClaim claim = claimService.requireClaim(claimId);
        if (!claim.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能提交自己的接取凭证");
        }
        ClaimStatus cur = ClaimStatus.of(claim.getStatus());
        boolean isCover = cur == ClaimStatus.SUBMITTED; // 审核前覆盖提交
        if (!(cur == ClaimStatus.CLAIMED || isCover)) {
            throw new BusinessException(ResultCode.CLAIM_NOT_SUBMITTABLE, "当前状态不可提交凭证");
        }
        Task task = taskMapper.selectById(claim.getTaskId());
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        if (!SUBMITTABLE_TASK_STATUSES.contains(task.getStatus())) {
            throw new BusinessException(ResultCode.CLAIM_NOT_SUBMITTABLE,
                    "任务当前状态（" + task.getStatus() + "）不可提交凭证");
        }

        // 2. 覆盖提交：复用最新一条有效提交，否则新建
        String fileIdsJson = serializeFileIds(req.getFileIds());
        TaskSubmission latest = submissionMapper.selectLatestByClaimId(claimId);
        if (latest != null) {
            TaskSubmission up = new TaskSubmission();
            up.setId(latest.getId());
            up.setContent(req.getContent());
            up.setFileIds(fileIdsJson);
            up.setSubmitTime(LocalDateTime.now());
            submissionMapper.updateById(up);
        } else {
            TaskSubmission s = new TaskSubmission();
            s.setClaimId(claimId);
            s.setContent(req.getContent());
            s.setFileIds(fileIdsJson);
            s.setSubmitTime(LocalDateTime.now());
            submissionMapper.insert(s);
        }

        // 3. CAS：CLAIMED → SUBMITTED（覆盖提交时跳过）
        if (!isCover) {
            int updated = taskClaimMapper.casStatus(claimId, ClaimStatus.CLAIMED.name(),
                    ClaimStatus.SUBMITTED.name());
            if (updated == 0) {
                throw new BusinessException(ResultCode.CLAIM_NOT_SUBMITTABLE, "接取状态已变化，请刷新");
            }
        }

        // 4. 时间戳：仅首次提交开审核窗口；覆盖提交不重置（防反复覆盖把审核截止后推）
        if (!isCover) {
            TaskClaim up = new TaskClaim();
            up.setId(claimId);
            up.setSubmittedAt(LocalDateTime.now());
            up.setReviewDeadline(LocalDateTime.now().plusHours(REVIEW_TIMEOUT_HOURS));
            taskClaimMapper.updateById(up);
        }

        // 5. 状态留痕：属于业务一致性，留在事务内
        claimService.writeClaimLog(claimId,
                isCover ? ClaimStatus.SUBMITTED.name() : ClaimStatus.CLAIMED.name(),
                ClaimStatus.SUBMITTED.name(), userId,
                isCover ? "覆盖更新凭证" : "提交完成凭证");

        // 6. 审计与通知属旁路操作：移到事务提交后执行
        final boolean cover = isCover;
        final Task submittedTask = task;
        AfterCommit.run(() -> {
            auditService.record(userId, "SUBMIT", "claim", claimId,
                    cover ? "覆盖提交凭证" : "提交凭证", httpReq);
            if (!cover) {
                notificationService.notify(submittedTask.getPublisherId(),
                        com.treatbord.module.notify.entity.Notification.TYPE_SUBMITTED,
                        "收到完成凭证",
                        "用户提交了任务《" + submittedTask.getTitle() + "》的完成凭证，请及时审核",
                        submittedTask.getId());
            }
        });

        // 提交可能改变任务状态（首次接取时任务已是 IN_PROGRESS，但列表页需刷新）→ 失效任务缓存
        taskCacheService.onTaskChanged(claim.getTaskId());
    }

    private String serializeFileIds(java.util.List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(fileIds);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "fileIds 格式错误");
        }
    }
}
