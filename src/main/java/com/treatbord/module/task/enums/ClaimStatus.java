package com.treatbord.module.task.enums;

import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;

import java.util.Map;
import java.util.Set;

/**
 * 接取状态机（AGENTS.md §6 / API_DESIGN.md §4-6）。
 *
 * CLAIMED →(提交)→ SUBMITTED →(通过)→ APPROVED
 * CLAIMED →(取消/超时)→ CANCELLED； SUBMITTED →(拒绝)→ REJECTED
 * SUBMITTED →(审核超时自动)→ APPROVED
 *
 * 复活（2026-10-02 修 bug）：CANCELLED / REJECTED → CLAIMED，表示用户重新接取同一任务。
 * 之所以必须有这条流转：task_claim 的唯一索引是 (task_id, user_id)（不含 status），
 * 所以「重新接取」只能复用原行，不可能插第二行。此前少了这条流转，
 * 重接会走 insert 撞唯一键、被报成 409「非法流转」，用户被永久锁死在任务外。
 * APPROVED 仍是终态（已完成结算，不允许复活）。
 */
public enum ClaimStatus {

    CLAIMED, SUBMITTED, APPROVED, REJECTED, CANCELLED;

    private static final Map<ClaimStatus, Set<ClaimStatus>> TRANSITIONS = Map.of(
            CLAIMED, Set.of(SUBMITTED, CANCELLED),
            SUBMITTED, Set.of(APPROVED, REJECTED),
            APPROVED, Set.of(),
            REJECTED, Set.of(CLAIMED),
            CANCELLED, Set.of(CLAIMED)
    );

    public static void validateTransition(ClaimStatus from, ClaimStatus to) {
        Set<ClaimStatus> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new BusinessException(ResultCode.CONFLICT,
                    String.format("接取状态非法流转: %s -> %s", from, to));
        }
    }

    public static ClaimStatus of(String s) {
        try {
            return ClaimStatus.valueOf(s);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.CONFLICT, "非法接取状态: " + s);
        }
    }
}