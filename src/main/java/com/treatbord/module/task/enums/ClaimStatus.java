package com.treatbord.module.task.enums;

import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;

import java.util.Map;
import java.util.Set;

/**
 * 接取状态机（docs/AGENTS.md §6 / API_DESIGN.md §4-6）。
 *
 * CLAIMED →(提交)→ SUBMITTED →(通过)→ APPROVED
 * CLAIMED →(取消/超时)→ CANCELLED； SUBMITTED →(拒绝)→ REJECTED
 * SUBMITTED →(审核超时自动)→ APPROVED
 */
public enum ClaimStatus {

    CLAIMED, SUBMITTED, APPROVED, REJECTED, CANCELLED;

    private static final Map<ClaimStatus, Set<ClaimStatus>> TRANSITIONS = Map.of(
            CLAIMED, Set.of(SUBMITTED, CANCELLED),
            SUBMITTED, Set.of(APPROVED, REJECTED),
            APPROVED, Set.of(),
            REJECTED, Set.of(),
            CANCELLED, Set.of()
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