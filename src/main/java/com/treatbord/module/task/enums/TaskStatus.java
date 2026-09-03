package com.treatbord.module.task.enums;

import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;

import java.util.Map;
import java.util.Set;

/**
 * 任务状态机（docs/AGENTS.md §6 / API_DESIGN.md §3）。
 *
 * OPEN →(有人接取)→ IN_PROGRESS →(提交齐)→ REVIEWING →(全部通过)→ SETTLED
 * OPEN →(接取截止无人)→ EXPIRED
 * OPEN/IN_PROGRESS →(发布者取消)→ CANCELLED
 */
public enum TaskStatus {

    OPEN, IN_PROGRESS, REVIEWING, SETTLED, EXPIRED, CANCELLED;

    /** 合法迁移表：K=from, V=to（与 AGENTS.md §6 对应） */
    private static final Map<TaskStatus, Set<TaskStatus>> TRANSITIONS = Map.of(
            OPEN, Set.of(IN_PROGRESS, EXPIRED, CANCELLED),
            IN_PROGRESS, Set.of(REVIEWING, EXPIRED, CANCELLED),
            REVIEWING, Set.of(SETTLED),
            SETTLED, Set.of(),
            EXPIRED, Set.of(),
            CANCELLED, Set.of()
    );

    /** 校验迁移合法性，非法抛 409 */
    public static void validateTransition(TaskStatus from, TaskStatus to) {
        Set<TaskStatus> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new BusinessException(ResultCode.CONFLICT,
                    String.format("任务状态非法流转: %s -> %s", from, to));
        }
    }

    public static TaskStatus of(String s) {
        try {
            return TaskStatus.valueOf(s);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.CONFLICT, "非法任务状态: " + s);
        }
    }
}