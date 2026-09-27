package com.treatbord.module.task.enums;

import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 接取状态机测试。
 */
class ClaimStatusTest {

    @ParameterizedTest(name = "{0} -> {1} 合法")
    @CsvSource({
            "CLAIMED,SUBMITTED",
            "CLAIMED,CANCELLED",
            "SUBMITTED,APPROVED",
            "SUBMITTED,REJECTED"
    })
    @DisplayName("合法流转不抛异常")
    void legalTransitions(ClaimStatus from, ClaimStatus to) {
        assertDoesNotThrow(() -> ClaimStatus.validateTransition(from, to));
    }

    @ParameterizedTest(name = "{0} -> {1} 非法")
    @CsvSource({
            "CLAIMED,APPROVED",
            "CLAIMED,REJECTED",
            "SUBMITTED,CANCELLED",
            "SUBMITTED,CLAIMED",
            "APPROVED,REJECTED",
            "REJECTED,APPROVED",
            "CANCELLED,CLAIMED"
    })
    @DisplayName("非法流转抛 409 业务异常")
    void illegalTransitions(ClaimStatus from, ClaimStatus to) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ClaimStatus.validateTransition(from, to));
        assertEquals(ResultCode.CONFLICT.getCode(), ex.getCode());
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = {"APPROVED", "REJECTED", "CANCELLED"})
    @DisplayName("终态不可再流转")
    void terminalStatesHaveNoWayOut(ClaimStatus terminal) {
        for (ClaimStatus to : ClaimStatus.values()) {
            assertThrows(BusinessException.class, () -> ClaimStatus.validateTransition(terminal, to));
        }
    }

    @Test
    @DisplayName("of() 解析非法值抛 409")
    void parse() {
        assertEquals(ClaimStatus.CLAIMED, ClaimStatus.of("CLAIMED"));
        assertEquals(ResultCode.CONFLICT.getCode(),
                assertThrows(BusinessException.class, () -> ClaimStatus.of("claimed")).getCode());
    }
}
