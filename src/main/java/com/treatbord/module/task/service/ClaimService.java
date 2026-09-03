package com.treatbord.module.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
    private final ClaimStatusLogMapper claimStatusLogMapper;
    private final TaskService taskService;
    private final UserService userService;
    private final AppConfigService appConfigService;
    private final AuditService auditService;

    /**
     * 接取任务（防超卖核心事务）。
     * 顺序：原子扣减 → 快照 → 归属/信用校验 → 插 claim → 任务 OPEN→IN_PROGRESS。
     * 唯一索引 (task_id,user_id) 兜底，冲突回滚（含原子扣减）。
     */
    @Transactional
    public Long claim(Long taskId, Long userId, HttpServletRequest httpReq) {
        // 1. 原子扣减名额（防超卖第一道防线）
        int updated = taskMapper.incrementClaimedCount(taskId);
        if (updated == 0) {
            // 区分：不存在 / 状态不可接取 / 满员
            Task t = taskMapper.selectById(taskId);
            if (t == null) {
                throw new BusinessException(ResultCode.TASK_NOT_FOUND);
            }
            boolean claimable = ClaimService.STATUS_OPEN.equals(t.getStatus())
                    || "IN_PROGRESS".equals(t.getStatus());
            if (claimable) {
                // 状态可接取但原子扣减失败 ⇒ 名额已满
                throw new BusinessException(ResultCode.TASK_FULL, "任务名额已满");
            }
            throw new BusinessException(ResultCode.TASK_NOT_CLAIMABLE);
        }

        // 2. 读取任务快照（reward 结算快照、publisher 校验）
        Task task = taskMapper.selectById(taskId);

        // 3. 防自接自单
        if (task.getPublisherId().equals(userId)) {
            // 回滚名额（事务会回滚，这里显式回退更安全）
            taskMapper.decrementClaimedCount(taskId);
            throw new BusinessException(ResultCode.SELF_CLAIM_FORBIDDEN);
        }

        // 4. 信用分门槛
        User user = userService.getById(userId);
        int threshold = appConfigService.getInt(CREDIT_THRESHOLD_KEY, 60);
        if (user.getCreditScore() < threshold) {
            taskMapper.decrementClaimedCount(taskId);
            throw new BusinessException(ResultCode.CREDIT_NOT_ENOUGH);
        }

        // 5. 显式防重复（唯一索引兜底）
        if (taskClaimMapper.countActiveClaim(taskId, userId) > 0) {
            taskMapper.decrementClaimedCount(taskId);
            throw new BusinessException(ResultCode.CLAIM_DUPLICATE);
        }

        // 6. 插入接取记录（reward 结算快照）
        TaskClaim claim = new TaskClaim();
        claim.setTaskId(taskId);
        claim.setUserId(userId);
        claim.setStatus(ClaimStatus.CLAIMED.name());
        claim.setReward(task.getReward());
        taskClaimMapper.insert(claim);

        // 7. 任务 OPEN → IN_PROGRESS（首次接取）
        if (STATUS_OPEN.equals(task.getStatus())) {
            int cas = taskMapper.casStatus(taskId, STATUS_OPEN, TaskStatus.IN_PROGRESS.name());
            if (cas > 0) {
                taskService.writeTaskLog(taskId, STATUS_OPEN, TaskStatus.IN_PROGRESS.name(), userId, "首次接取");
            }
        }

        // 8. 接取状态审计
        writeClaimLog(claim.getId(), null, ClaimStatus.CLAIMED.name(), userId, "接取任务");
        auditService.record(userId, "CLAIM_TASK", "task", taskId,
                "接取任务 reward=" + claim.getReward(), httpReq);

        return claim.getId();
    }

    /**
     * 取消接取：仅 CLAIMED（未提交）可取消；claimed_count 回减。
     */
    @Transactional
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
        auditService.record(userId, "CANCEL_CLAIM", "claim", claimId, "取消接取", httpReq);
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
}