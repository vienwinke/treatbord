package com.treatbord.module.schedule.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.treatbord.module.config.service.AppConfigService;
import com.treatbord.module.notify.service.NotificationService;
import com.treatbord.module.review.service.ReviewService;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务（docs/API_DESIGN.md §12）。
 * ⚠️ 类名刻意避开 taskScheduler：Spring Boot 自动配置占用该 bean 名。
 * 幂等：所有迁移走 CAS + 状态检查，可重复执行；防重入依赖 @Scheduled 单实例
 * （多实例部署时需 Redisson 分布式锁，MVP 单实例）。
 * 时间统一用数据库 NOW()（SQL 内判断），避免应用时钟偏差。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final TaskMapper taskMapper;
    private final TaskClaimMapper taskClaimMapper;
    private final TaskService taskService;
    private final ClaimService claimService;
    private final AppConfigService appConfigService;
    private final NotificationService notificationService;
    private final ReviewService reviewService;
    private final UserMapper userMapper;

    /**
     * 任务过期扫描（每分钟）：
     * 1. claim_deadline 已过仍 OPEN → EXPIRED（无接取）
     * 2. deadline 已过 IN_PROGRESS → EXPIRED
     */
    @Scheduled(cron = "0 * * * * *")
    public void expireTasks() {
        try {
            int n1 = expireByStatus(TaskStatus.OPEN, "claim_deadline < NOW()", "接取截止已过自动过期");
            int n2 = expireByStatus(TaskStatus.IN_PROGRESS, "deadline < NOW()", "完成截止已过自动过期");
            if (n1 > 0 || n2 > 0) {
                log.info("[SCHED] expireTasks: OPEN→EXPIRED={}, IN_PROGRESS→EXPIRED={}", n1, n2);
            }
        } catch (Exception e) {
            log.error("[SCHED] expireTasks 失败", e);
        }
    }

    /**
     * 逐条 CAS 过期 + 写 task_status_log：
     * 时间判定交给数据库 NOW()（避免应用时钟偏差），并补齐状态审计（AGENTS §6：所有迁移写 status_log）。
     * 条件串是代码内常量（非用户输入），不存在注入风险。
     */
    private int expireByStatus(TaskStatus fromStatus, String timeCondition, String reason) {
        List<Task> overdue = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, fromStatus.name())
                .apply(timeCondition));
        int n = 0;
        for (Task t : overdue) {
            if (taskMapper.casStatus(t.getId(), fromStatus.name(), TaskStatus.EXPIRED.name()) == 0) {
                continue; // 已被并发处理
            }
            taskService.writeTaskLog(t.getId(), fromStatus.name(), TaskStatus.EXPIRED.name(), null, reason);
            n++;
        }
        return n;
    }

    /**
     * 定时任务收尾：自动通过 / 超时取消之后同样要判定任务该推进还是该过期，
     * 否则任务会永久停在 IN_PROGRESS（settlement 永不生成）。
     */
    private void finalizeTasks(java.util.Set<Long> taskIds) {
        for (Long taskId : taskIds) {
            Task t = taskMapper.selectById(taskId);
            if (t == null) {
                continue;
            }
            try {
                reviewService.finalizeTaskIfNeeded(t, null, null);
            } catch (Exception e) {
                log.warn("[SCHED] 任务收尾失败 task={}", taskId, e);
            }
        }
    }

    /**
     * 接取超时扫描（每分钟）：接取后 N 小时未提交 → claim CANCELLED + 扣信用分 + 名额回减。
     */
    @Scheduled(cron = "0 * * * * *")
    public void cancelOverdueClaims() {
        try {
            int timeoutHours = appConfigService.getInt("claim.timeout.hours", 72);
            LocalDateTime cutoff = LocalDateTime.now().minusHours(timeoutHours);

            List<TaskClaim> overdue = taskClaimMapper.selectList(new LambdaQueryWrapper<TaskClaim>()
                    .eq(TaskClaim::getStatus, ClaimStatus.CLAIMED.name())
                    .lt(TaskClaim::getCreateTime, cutoff));

            java.util.Set<Long> affectedTaskIds = new java.util.HashSet<>();
            for (TaskClaim c : overdue) {
                int updated = taskClaimMapper.casStatus(c.getId(), ClaimStatus.CLAIMED.name(),
                        ClaimStatus.CANCELLED.name());
                if (updated == 0) {
                    continue; // 已被并发处理
                }
                affectedTaskIds.add(c.getTaskId());
                taskMapper.decrementClaimedCount(c.getTaskId());
                claimService.writeClaimLog(c.getId(), ClaimStatus.CLAIMED.name(),
                        ClaimStatus.CANCELLED.name(), null, "接取超时自动取消");

                // 扣信用分
                int penalty = appConfigService.getInt("credit.penalty.overdue", 10);
                User u = userMapper.selectById(c.getUserId());
                if (u != null) {
                    User up = new User();
                    up.setId(u.getId());
                    up.setCreditScore(Math.max(0, u.getCreditScore() - penalty));
                    userMapper.updateById(up);
                }
                log.info("[SCHED] cancelOverdueClaims: claim={} 超时取消", c.getId());
            }
            finalizeTasks(affectedTaskIds);
        } catch (Exception e) {
            log.error("[SCHED] cancelOverdueClaims 失败", e);
        }
    }

    /**
     * 审核超时扫描（每分钟）：SUBMITTED 后 48h 未审 → 自动 APPROVED + 通知。
     */
    @Scheduled(cron = "0 * * * * *")
    public void autoApprove() {
        try {
            boolean enabled = appConfigService.getBool("auto.approve.enabled", true);
            if (!enabled) {
                return;
            }
            int timeoutHours = appConfigService.getInt("review.timeout.hours", 48);
            LocalDateTime cutoff = LocalDateTime.now().minusHours(timeoutHours);

            List<TaskClaim> overdue = taskClaimMapper.selectList(new LambdaQueryWrapper<TaskClaim>()
                    .eq(TaskClaim::getStatus, ClaimStatus.SUBMITTED.name())
                    .lt(TaskClaim::getSubmittedAt, cutoff));

            java.util.Set<Long> affectedTaskIds = new java.util.HashSet<>();
            for (TaskClaim c : overdue) {
                int updated = taskClaimMapper.casStatus(c.getId(), ClaimStatus.SUBMITTED.name(),
                        ClaimStatus.APPROVED.name());
                if (updated == 0) {
                    continue;
                }
                affectedTaskIds.add(c.getTaskId());
                // 与人工审核保持一致：回填审核时间与备注
                TaskClaim up = new TaskClaim();
                up.setId(c.getId());
                up.setReviewedAt(LocalDateTime.now());
                up.setReviewNote("审核超时自动通过");
                taskClaimMapper.updateById(up);
                claimService.writeClaimLog(c.getId(), ClaimStatus.SUBMITTED.name(),
                        ClaimStatus.APPROVED.name(), null, "审核超时自动通过");
                notificationService.notify(c.getUserId(),
                        com.treatbord.module.notify.entity.Notification.TYPE_AUTO_APPROVED,
                        "凭证已自动通过",
                        "审核超时，你的凭证已被自动标记为通过",
                        c.getTaskId());
                log.info("[SCHED] autoApprove: claim={} 自动通过", c.getId());
            }
            finalizeTasks(affectedTaskIds);
        } catch (Exception e) {
            log.error("[SCHED] autoApprove 失败", e);
        }
    }

    /**
     * 对账扫描（每小时）：claimed_count 与实际 claim 行数不一致 → 告警记录。
     * MVP 仅记日志，后续接告警通道。
     */
    @Scheduled(cron = "0 0 * * * *")
    public void reconcile() {
        try {
            List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                    .in(Task::getStatus, TaskStatus.OPEN.name(), TaskStatus.IN_PROGRESS.name()));
            for (Task t : tasks) {
                long active = taskClaimMapper.selectCount(new LambdaQueryWrapper<TaskClaim>()
                        .eq(TaskClaim::getTaskId, t.getId())
                        .in(TaskClaim::getStatus,
                                ClaimStatus.CLAIMED.name(), ClaimStatus.SUBMITTED.name(),
                                ClaimStatus.APPROVED.name()));
                if (active != t.getClaimedCount()) {
                    log.warn("[SCHED] 对账异常 task={} claimed_count={} 实际有效claim={}",
                            t.getId(), t.getClaimedCount(), active);
                }
            }
        } catch (Exception e) {
            log.error("[SCHED] reconcile 失败", e);
        }
    }
}