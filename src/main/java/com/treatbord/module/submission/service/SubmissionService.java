package com.treatbord.module.submission.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.audit.service.AuditService;
import com.treatbord.module.file.entity.FileRecord;
import com.treatbord.module.file.mapper.FileRecordMapper;
import com.treatbord.module.notify.service.NotificationService;
import com.treatbord.module.security.service.ContentSecurityService;
import com.treatbord.module.submission.dto.SubmissionRequest;
import com.treatbord.module.submission.dto.SubmissionVO;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 凭证服务（docs/API_DESIGN.md §5）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private static final long REVIEW_TIMEOUT_HOURS = 48;

    /** 可提交凭证的任务状态（跨聚合校验用） */
    private static final java.util.Set<String> SUBMITTABLE_TASK_STATUSES =
            java.util.Set.of("OPEN", "IN_PROGRESS", "REVIEWING");

    private final TaskSubmissionMapper submissionMapper;
    private final TaskClaimMapper taskClaimMapper;
    private final TaskMapper taskMapper;
    private final FileRecordMapper fileRecordMapper;
    private final ClaimService claimService;
    private final ContentSecurityService contentSecurityService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * 提交完成凭证：CLAIMED → SUBMITTED（CAS）。
     * 审核前可覆盖：重复提交更新/替换最新一条有效提交。
     */
    @Transactional
    public void submit(Long claimId, Long userId, SubmissionRequest req, HttpServletRequest httpReq) {
        // 1. 归属校验（IDOR）：只能提交自己的接取
        TaskClaim claim = claimService.requireClaim(claimId);
        if (!claim.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能提交自己的接取凭证");
        }

        // 2. 状态机：仅 CLAIMED / SUBMITTED（覆盖）可提交
        ClaimStatus cur = ClaimStatus.of(claim.getStatus());
        boolean isCover = cur == ClaimStatus.SUBMITTED; // 审核前覆盖提交
        if (!(cur == ClaimStatus.CLAIMED || isCover)) {
            throw new BusinessException(ResultCode.CLAIM_NOT_SUBMITTABLE, "当前状态不可提交凭证");
        }

        // 2.1 跨聚合校验：任务必须仍处于可提交阶段（防对已取消/已过期任务提交凭证）
        Task task = taskMapper.selectById(claim.getTaskId());
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        if (!SUBMITTABLE_TASK_STATUSES.contains(task.getStatus())) {
            throw new BusinessException(ResultCode.CLAIM_NOT_SUBMITTABLE,
                    "任务当前状态（" + task.getStatus() + "）不可提交凭证");
        }

        // 2.2 凭证文件校验：必须存在、属于本人、且未被内容安全判定违规
        validateFileIds(req.getFileIds(), userId);

        // 3. 内容安全（文本；图片已在上传时通过 mediaCheckAsync 异步检测）
        contentSecurityService.checkText(req.getContent(), "submission", userId);

        // 4. 覆盖提交：复用最新一条有效提交，否则新建
        TaskSubmission latest = submissionMapper.selectLatestByClaimId(claimId);
        String fileIdsJson = serializeFileIds(req.getFileIds());
        if (latest != null) {
            // 覆盖：更新内容与 submit_time
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

        // 5. 接取状态流转：CLAIMED → SUBMITTED（CAS 防竞态）；SUBMITTED 覆盖时跳过
        if (!isCover) {
            int updated = taskClaimMapper.casStatus(claimId, ClaimStatus.CLAIMED.name(), ClaimStatus.SUBMITTED.name());
            if (updated == 0) {
                throw new BusinessException(ResultCode.CLAIM_NOT_SUBMITTABLE, "接取状态已变化，请刷新");
            }
        }

        // 6. 时间戳：仅首次提交开审核窗口；覆盖提交不重置（防提交者反复覆盖把审核截止一直后推）
        if (!isCover) {
            TaskClaim up = new TaskClaim();
            up.setId(claimId);
            up.setSubmittedAt(LocalDateTime.now());
            up.setReviewDeadline(LocalDateTime.now().plusHours(REVIEW_TIMEOUT_HOURS));
            taskClaimMapper.updateById(up);
        }

        // 7. 审计 + 通知发布者（覆盖提交不重复通知，仅首次提交通知）
        claimService.writeClaimLog(claimId,
                isCover ? ClaimStatus.SUBMITTED.name() : ClaimStatus.CLAIMED.name(),
                ClaimStatus.SUBMITTED.name(), userId,
                isCover ? "覆盖更新凭证" : "提交完成凭证");
        auditService.record(userId, "SUBMIT", "claim", claimId,
                isCover ? "覆盖提交凭证" : "提交凭证", httpReq);

        if (!isCover) {
            notificationService.notify(task.getPublisherId(),
                    com.treatbord.module.notify.entity.Notification.TYPE_SUBMITTED,
                    "收到完成凭证",
                    "用户提交了任务《" + task.getTitle() + "》的完成凭证，请及时审核",
                    task.getId());
        }
    }

    /**
     * 查看接取详情（含最新凭证）。
     * 归属：claim.user_id == 当前用户 或 task.publisher_id == 当前用户。
     */
    public TaskClaimAndSubmission detail(Long claimId, Long currentUserId) {
        TaskClaim claim = claimService.requireClaim(claimId);
        Task task = taskMapper.selectById(claim.getTaskId());

        boolean isClaimer = claim.getUserId().equals(currentUserId);
        boolean isPublisher = task != null && task.getPublisherId().equals(currentUserId);
        if (!isClaimer && !isPublisher) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该接取记录");
        }

        TaskSubmission latest = submissionMapper.selectLatestByClaimId(claimId);
        SubmissionVO vo = SubmissionVO.from(latest);
        if (vo != null && latest.getFileIds() != null) {
            vo.setFileIds(parseFileIds(latest.getFileIds()));
        }
        return new TaskClaimAndSubmission(claim, task, vo);
    }

    /**
     * 校验凭证引用的文件：必须存在（未逻辑删除）、属于当前用户、且未被内容安全判定违规。
     * 防「引用他人文件」与「引用违规文件绕过内容安全」。
     */
    private void validateFileIds(java.util.List<Long> fileIds, Long userId) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        java.util.Set<Long> distinct = new java.util.LinkedHashSet<>(fileIds);
        if (distinct.contains(null)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "fileIds 含空元素");
        }
        java.util.List<FileRecord> files = fileRecordMapper.selectBatchIds(distinct);
        if (files.size() != distinct.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "存在无效或已删除的 fileId");
        }
        for (FileRecord f : files) {
            if (!userId.equals(f.getUploaderId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能引用自己上传的文件");
            }
            if (Integer.valueOf(FileRecord.SEC_CHECK_REJECT).equals(f.getSecStatus())) {
                throw new BusinessException(ResultCode.FILE_CONTENT_ILLEGAL);
            }
        }
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

    private java.util.List<Long> parseFileIds(String json) {
        if (json == null || json.isBlank()) {
            return java.util.List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<java.util.List<Long>>() {
            });
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    /** 承载 claim + 关联任务 + 凭证的聚合视图 */
    @lombok.Value
    public static class TaskClaimAndSubmission {
        TaskClaim claim;
        Task task;
        SubmissionVO submission;
    }
}