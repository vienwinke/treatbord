package com.treatbord.module.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.treatbord.common.AfterCommit;
import com.treatbord.common.BusinessException;
import com.treatbord.common.PageResult;
import com.treatbord.common.ResultCode;
import com.treatbord.module.audit.service.AuditService;
import com.treatbord.module.config.service.AppConfigService;
import com.treatbord.module.task.dto.ClaimVO;
import com.treatbord.module.task.entity.Task;
import com.treatbord.module.task.entity.TaskClaim;
import com.treatbord.module.task.entity.TaskStatusLog;
import com.treatbord.module.task.enums.ClaimStatus;
import com.treatbord.module.task.enums.TaskStatus;
import com.treatbord.module.task.mapper.ClaimStatusLogMapper;
import com.treatbord.module.task.mapper.TaskClaimMapper;
import com.treatbord.module.task.mapper.TaskMapper;
import com.treatbord.module.user.entity.User;
import com.treatbord.module.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 接取服务（docs/API_DESIGN.md §4）。
 * 接取是防超卖核心：原子扣减 + 唯一索引 + CAS 状态，同一事务内完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimService {

    private static final String CREDIT_THRESHOLD_KEY = "credit.claim.threshold";

    private final TaskMapper taskMapper;
    private final TaskClaimMapper taskClaimMapper;
    private final TaskCacheService taskCacheService;
    private final ClaimStatusLogMapper claimStatusLogMapper;
    private final TaskService taskService;
    private final UserService userService;
    private final AppConfigService appConfigService;
    private final AuditService auditService;

    /**
     * 接取任务（防超卖核心事务）。
     * 顺序：查既有行 → 原子扣减（或仅校验可接取）→ 快照 → 归属/信用校验 → 插行或复活。
     * 唯一索引 (task_id,user_id) 兜底，冲突回滚（含原子扣减）。
     *
     * 重新接取（2026-10-02 修 bug）：取消/驳回后再次接取同一任务时**复用原行** ——
     * task_claim 的唯一索引是 (task_id,user_id)，不含 status，插第二行必然撞键；
     * 旧实现因此把「重接」报成 409「非法流转」，用户被永久锁死在该任务外。
     * 名额语义按「是否已回减」区分：CANCELLED 时取消已回减 → 需重新扣减；
     * REJECTED 时驳回不回减 → 只校验不再扣减，避免 claimed_count 虚高。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long claim(Long taskId, Long userId, HttpServletRequest httpReq) {
        // 0. 先查该用户在该任务的既有行（唯一索引保证至多一条）
        TaskClaim existing = taskClaimMapper.selectOne(new LambdaQueryWrapper<TaskClaim>()
                .eq(TaskClaim::getTaskId, taskId)
                .eq(TaskClaim::getUserId, userId));

        if (existing != null && !REACTIVATABLE.contains(existing.getStatus())) {
            // CLAIMED / SUBMITTED / APPROVED：确实已有有效接取
            throw new BusinessException(ResultCode.CLAIM_DUPLICATE);
        }
        boolean reactivate = existing != null;
        boolean slotReleased = reactivate && ClaimStatus.CANCELLED.name().equals(existing.getStatus());

        if (!reactivate || slotReleased) {
            // 1. 原子扣减名额（防超卖第一道防线）
            if (taskMapper.incrementClaimedCount(taskId) == 0) {
                throw claimabilityFailure(taskId);
            }
        } else if (taskMapper.countClaimable(taskId) == 0) {
            // 1'. 驳回后复活：名额仍被本人占用，只校验任务仍可接取，不能再扣一次
            throw claimabilityFailure(taskId);
        }

        // 2. 读取任务快照（reward 结算快照、publisher 校验）
        Task task = taskMapper.selectById(taskId);

        // 3. 防自接自单
        if (task.getPublisherId().equals(userId)) {
            // 无需手动回退：BusinessException 是运行时异常，@Transactional 会回滚本次原子扣减
            throw new BusinessException(ResultCode.SELF_CLAIM_FORBIDDEN);
        }

        // 4. 信用分门槛
        User user = userService.getById(userId);
        int threshold = appConfigService.getInt(CREDIT_THRESHOLD_KEY, 60);
        if (user.getCreditScore() < threshold) {
            throw new BusinessException(ResultCode.CREDIT_NOT_ENOUGH);
        }

        // 5. 插新行，或复活既有行（CAS，防并发重复复活）
        Long claimId;
        String fromStatus = null;
        if (reactivate) {
            ClaimStatus from = ClaimStatus.of(existing.getStatus());
            ClaimStatus.validateTransition(from, ClaimStatus.CLAIMED);
            int cas = taskClaimMapper.reactivate(existing.getId(), existing.getStatus(),
                    ClaimStatus.CLAIMED.name(), task.getReward());
            if (cas == 0) {
                throw new BusinessException(ResultCode.CONFLICT, "接取状态已变化，请重试");
            }
            claimId = existing.getId();
            fromStatus = existing.getStatus();
        } else {
            TaskClaim claim = new TaskClaim();
            claim.setTaskId(taskId);
            claim.setUserId(userId);
            claim.setStatus(ClaimStatus.CLAIMED.name());
            claim.setReward(task.getReward());
            taskClaimMapper.insert(claim);
            claimId = claim.getId();
        }

        // 6. 任务 OPEN → IN_PROGRESS（首次接取）
        if (STATUS_OPEN.equals(task.getStatus())) {
            int cas = taskMapper.casStatus(taskId, STATUS_OPEN, TaskStatus.IN_PROGRESS.name());
            if (cas > 0) {
                taskService.writeTaskLog(taskId, STATUS_OPEN, TaskStatus.IN_PROGRESS.name(), userId, "首次接取");
            }
        }

        // 7. 接取状态审计（复活时 from=CANCELLED/REJECTED，保留完整轨迹）
        writeClaimLog(claimId, fromStatus, ClaimStatus.CLAIMED.name(), userId,
                reactivate ? "重新接取任务" : "接取任务");
        // 审计属旁路操作：移到事务提交后执行（不占用事务时间，回滚时也不会留下假记录）
        AfterCommit.run(() -> auditService.record(userId, "CLAIM_TASK", "task", taskId,
                "接取任务 reward=" + task.getReward() + (reactivate ? "（重新接取）" : ""), httpReq));

        // 名额与任务状态已变 → 失效任务缓存
        taskCacheService.onTaskChanged(taskId);

        return claimId;
    }

    /**
     * 扣减 / 可接取校验失败时给出准确错误码（不存在 / 状态不可接 / 已过截止 / 满员）。
     * 时间条件已由原子 SQL 用 DB NOW() 判定，这里只为给出正确的错误码。
     */
    private BusinessException claimabilityFailure(Long taskId) {
        Task t = taskMapper.selectById(taskId);
        if (t == null) {
            return new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        boolean claimable = STATUS_OPEN.equals(t.getStatus()) || "IN_PROGRESS".equals(t.getStatus());
        if (!claimable) {
            return new BusinessException(ResultCode.TASK_NOT_CLAIMABLE);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean deadlinePassed = t.getClaimDeadline() == null || !t.getClaimDeadline().isAfter(now)
                || t.getDeadline() == null || !t.getDeadline().isAfter(now);
        if (deadlinePassed) {
            return new BusinessException(ResultCode.TASK_NOT_CLAIMABLE, "任务已过接取/完成截止时间");
        }
        return new BusinessException(ResultCode.TASK_FULL, "任务名额已满");
    }

    /**
     * 取消接取：仅 CLAIMED（未提交）可取消；claimed_count 回减。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelClaim(Long claimId, Long userId, HttpServletRequest httpReq) {
        TaskClaim claim = requireClaim(claimId);
        // 归属校验（IDOR）
        if (!claim.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能取消自己的接取");
        }
        ClaimStatus.validateTransition(ClaimStatus.of(claim.getStatus()), ClaimStatus.CANCELLED);

        // CAS：仅 CLAIMED → CANCELLED
        int updated = taskClaimMapper.casStatus(claimId, ClaimStatus.CLAIMED.name(), ClaimStatus.CANCELLED.name());
        if (updated == 0) {
            throw new BusinessException(ResultCode.CLAIM_CANCEL_NOT_ALLOWED, "当前状态不可取消");
        }
        // 名额回减
        taskMapper.decrementClaimedCount(claim.getTaskId());

        writeClaimLog(claimId, ClaimStatus.CLAIMED.name(), ClaimStatus.CANCELLED.name(), userId, "用户取消接取");
        AfterCommit.run(() -> auditService.record(userId, "CANCEL_CLAIM", "claim", claimId, "取消接取", httpReq));
        // 名额已回减 → 失效任务缓存
        taskCacheService.onTaskChanged(claim.getTaskId());
    }

    /**
     * 我接取的任务（分页），附任务摘要。
     */
    public PageResult<ClaimVO> myClaims(Long userId, long page, long pageSize) {
        Page<TaskClaim> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<TaskClaim> qw = new LambdaQueryWrapper<TaskClaim>()
                .eq(TaskClaim::getUserId, userId)
                .orderByDesc(TaskClaim::getCreateTime);
        Page<TaskClaim> result = taskClaimMapper.selectPage(p, qw);

        List<ClaimVO> vos = result.getRecords().stream()
                .map(ClaimVO::fromClaim)
                .collect(Collectors.toList());

        // 联查任务摘要
        List<Long> taskIds = vos.stream().map(ClaimVO::getTaskId).distinct().toList();
        Map<Long, Task> tasks = taskIds.isEmpty() ? Map.of()
                : taskMapper.selectBatchIds(taskIds).stream()
                    .collect(Collectors.toMap(Task::getId, Function.identity()));
        vos.forEach(v -> {
            Task t = tasks.get(v.getTaskId());
            if (t != null) {
                v.setTaskTitle(t.getTitle());
                v.setTaskDeadline(t.getDeadline());
                v.setTaskStatus(t.getStatus());
            }
        });
        return PageResult.of(vos, result.getTotal(), page, pageSize);
    }

    /** 写接取状态审计 */
    public void writeClaimLog(Long claimId, String from, String to, Long operatorId, String reason) {
        var log = new com.treatbord.module.task.entity.ClaimStatusLog();
        log.setClaimId(claimId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorId(operatorId);
        log.setReason(reason);
        claimStatusLogMapper.insert(log);
    }

    public TaskClaim requireClaim(Long claimId) {
        TaskClaim claim = taskClaimMapper.selectById(claimId);
        if (claim == null) {
            throw new BusinessException(ResultCode.CLAIM_NOT_FOUND);
        }
        return claim;
    }

    private static final String STATUS_OPEN = "OPEN";

    /** 可被「重新接取」复活的状态：CANCELLED（取消时名额已回减）与 REJECTED（名额未回减） */
    private static final java.util.Set<String> REACTIVATABLE =
            java.util.Set.of(ClaimStatus.CANCELLED.name(), ClaimStatus.REJECTED.name());
}