package com.treatbord.module.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.audit.service.AuditService;
import com.treatbord.module.config.service.AppConfigService;
import com.treatbord.module.notify.service.NotificationService;
import com.treatbord.module.settlement.entity.Settlement;
import com.treatbord.module.settlement.mapper.SettlementMapper;
import com.treatbord.module.task.entity.Task;
import com.treatbord.module.task.entity.TaskClaim;
import com.treatbord.module.task.enums.ClaimStatus;
import com.treatbord.module.task.enums.TaskStatus;
import com.treatbord.module.task.mapper.TaskClaimMapper;
import com.treatbord.module.task.mapper.TaskMapper;
import com.treatbord.module.task.service.ClaimService;
import com.treatbord.module.task.service.TaskService;
import com.treatbord.module.user.entity.User;
import com.treatbord.module.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审核服务（docs/API_DESIGN.md §6）。
 * approve/reject 后做任务收尾判定：无进行中 claim 时推进任务状态并生成结算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    /** 可审核凭证的任务状态（跨聚合校验用） */
    private static final java.util.Set<String> REVIEWABLE_TASK_STATUSES =
            java.util.Set.of("OPEN", "IN_PROGRESS", "REVIEWING");

    private final TaskClaimMapper taskClaimMapper;
    private final TaskMapper taskMapper;
    private final ClaimService claimService;
    private final TaskService taskService;
    private final SettlementMapper settlementMapper;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final AppConfigService appConfigService;
    private final UserMapper userMapper;

    /**
     * 审核凭证 approve/reject。
     */
    @Transactional
    public void review(Long claimId, Long reviewerId, String action, String note,
                       HttpServletRequest httpReq) {
        TaskClaim claim = claimService.requireClaim(claimId);
        Task task = taskMapper.selectById(claim.getTaskId());
        if (task == null || !task.getPublisherId().equals(reviewerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有任务发布者可以审核");
        }
        // 跨聚合校验：任务已取消/已过期/已结算时不允许再审核
        if (!REVIEWABLE_TASK_STATUSES.contains(task.getStatus())) {
            throw new BusinessException(ResultCode.CLAIM_NOT_REVIEWABLE,
                    "任务当前状态（" + task.getStatus() + "）不可审核");
        }

        ClaimStatus claimStatus = ClaimStatus.of(claim.getStatus());
        ClaimStatus target = "approve".equalsIgnoreCase(action) ? ClaimStatus.APPROVED : ClaimStatus.REJECTED;
        ClaimStatus.validateTransition(claimStatus, target);

        int updated = taskClaimMapper.casStatus(claimId, claim.getStatus(), target.name());
        if (updated == 0) {
            throw new BusinessException(ResultCode.CLAIM_NOT_REVIEWABLE, "接取状态已变化，请刷新");
        }

        TaskClaim up = new TaskClaim();
        up.setId(claimId);
        up.setReviewedAt(LocalDateTime.now());
        up.setReviewNote(note);
        taskClaimMapper.updateById(up);

        if (target == ClaimStatus.REJECTED) {
            int penalty = appConfigService.getInt("credit.penalty.reject", 5);
            User u = userMapper.selectById(claim.getUserId());
            if (u != null) {
                User upUser = new User();
                upUser.setId(u.getId());
                upUser.setCreditScore(Math.max(0, u.getCreditScore() - penalty));
                userMapper.updateById(upUser);
            }
        }

        claimService.writeClaimLog(claimId, claim.getStatus(), target.name(), reviewerId,
                "审核" + (target == ClaimStatus.APPROVED ? "通过" : "驳回") + (note == null ? "" : ": " + note));
        auditService.record(reviewerId, "REVIEW_" + target.name(), "claim", claimId,
                "审核凭证: " + action, httpReq);
        notificationService.notify(claim.getUserId(),
                com.treatbord.module.notify.entity.Notification.TYPE_REVIEW_RESULT,
                target == ClaimStatus.APPROVED ? "凭证审核通过" : "凭证被驳回",
                "你的任务《" + task.getTitle() + "》凭证" +
                        (target == ClaimStatus.APPROVED ? "已通过审核" : "被驳回" +
                        (note == null ? "" : "，原因：" + note)),
                task.getId());

        finalizeTaskIfNeeded(task, reviewerId, httpReq);
    }

    /**
     * 任务收尾判定：审核（本类 review）与定时任务（审核超时自动通过 / 接取超时取消）共用。
     * 外部（定时任务）调用时 @Transactional 生效；review() 内部调用时已在同一事务中。
     */
    @Transactional
    public void finalizeTaskIfNeeded(Task task, Long operatorId, HttpServletRequest httpReq) {
        long active = taskClaimMapper.selectCount(new LambdaQueryWrapper<TaskClaim>()
                .eq(TaskClaim::getTaskId, task.getId())
                .in(TaskClaim::getStatus, ClaimStatus.CLAIMED.name(), ClaimStatus.SUBMITTED.name()));
        if (active > 0) {
            return;
        }
        if (!"IN_PROGRESS".equals(task.getStatus()) && !"OPEN".equals(task.getStatus())) {
            return;
        }

        long approved = taskClaimMapper.selectCount(new LambdaQueryWrapper<TaskClaim>()
                .eq(TaskClaim::getTaskId, task.getId())
                .eq(TaskClaim::getStatus, ClaimStatus.APPROVED.name()));

        if (approved > 0) {
            int cas = taskMapper.casStatus(task.getId(), task.getStatus(), TaskStatus.REVIEWING.name());
            if (cas > 0) {
                taskService.writeTaskLog(task.getId(), task.getStatus(), TaskStatus.REVIEWING.name(),
                        operatorId, "全部接取审核完毕");
                settleApproved(task, operatorId, httpReq);
            }
        } else {
            if ("IN_PROGRESS".equals(task.getStatus())) {
                int cas = taskMapper.casStatus(task.getId(), task.getStatus(), TaskStatus.EXPIRED.name());
                if (cas > 0) {
                    taskService.writeTaskLog(task.getId(), task.getStatus(), TaskStatus.EXPIRED.name(),
                            operatorId, "无凭证审核通过");
                    notificationService.notify(task.getPublisherId(),
                            com.treatbord.module.notify.entity.Notification.TYPE_TASK_EXPIRED,
                            "任务已过期",
                            "任务《" + task.getTitle() + "》无有效完成，已标记过期",
                            task.getId());
                }
            }
        }
    }

    private void settleApproved(Task task, Long operatorId, HttpServletRequest httpReq) {
        List<TaskClaim> approvedClaims = taskClaimMapper.selectList(new LambdaQueryWrapper<TaskClaim>()
                .eq(TaskClaim::getTaskId, task.getId())
                .eq(TaskClaim::getStatus, ClaimStatus.APPROVED.name()));

        // 幂等：已生成过结算的 claim 跳过（定时任务重跑/并发收尾时不产生重复结算）
        java.util.Set<Long> settledClaimIds = settlementMapper.selectList(
                        new LambdaQueryWrapper<Settlement>().eq(Settlement::getTaskId, task.getId()))
                .stream().map(Settlement::getClaimId).collect(java.util.stream.Collectors.toSet());
        for (TaskClaim c : approvedClaims) {
            if (settledClaimIds.contains(c.getId())) {
                continue;
            }
            Settlement s = new Settlement();
            s.setClaimId(c.getId());
            s.setTaskId(task.getId());
            s.setUserId(c.getUserId());
            s.setAmount(c.getReward());
            s.setStatus(Settlement.STATUS_PENDING);
            settlementMapper.insert(s);
        }
        int cas = taskMapper.casStatus(task.getId(), TaskStatus.REVIEWING.name(), TaskStatus.SETTLED.name());
        if (cas > 0) {
            taskService.writeTaskLog(task.getId(), TaskStatus.REVIEWING.name(), TaskStatus.SETTLED.name(),
                    operatorId, "结算生成");
            auditService.record(operatorId, "SETTLE", "task", task.getId(),
                    "生成 " + approvedClaims.size() + " 笔结算", httpReq);
        }
    }
}
