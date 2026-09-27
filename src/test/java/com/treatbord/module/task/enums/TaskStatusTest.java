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
 * 任务状态机测试：合法流转放行、非法流转一律 409、终态不可再流转。
 * 纯逻辑，无 Spring 依赖。
 */
class TaskStatusTest {

    @ParameterizedTest(name = "{0} -> {1} 合法")
    @CsvSource({
            "OPEN,IN_PROGRESS",
            "OPEN,EXPIRED",
            "OPEN,CANCELLED",
            "IN_PROGRESS,REVIEWING",
            "IN_PROGRESS,EXPIRED",
            "IN_PROGRESS,CANCELLED",
            "REVIEWING,SETTLED"
    })
    @DisplayName("合法流转不抛异常")
    void legalTransitions(TaskStatus from, TaskStatus to) {
        assertDoesNotThrow(() -> TaskStatus.validateTransition(from, to));
    }

    @ParameterizedTest(name = "{0} -> {1} 非法")
    @CsvSource({
            "OPEN,SETTLED",
            "OPEN,REVIEWING",
            "OPEN,OPEN",
            "IN_PROGRESS,SETTLED",
            "IN_PROGRESS,OPEN",
            "REVIEWING,CANCELLED",
            "REVIEWING,EXPIRED",
            "SETTLED,OPEN",
            "EXPIRED,OPEN",
            "CANCELLED,OPEN"
    })
    @DisplayName("非法流转抛 409 业务异常")
    void illegalTransitions(TaskStatus from, TaskStatus to) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> TaskStatus.validateTransition(from, to));
        assertEquals(ResultCode.CONFLICT.getCode(), ex.getCode());
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"SETTLED", "EXPIRED", "CANCELLED"})
    @DisplayName("终态不可再流转到任何状态")
    void terminalStatesHaveNoWayOut(TaskStatus terminal) {
        for (TaskStatus to : TaskStatus.values()) {
            assertThrows(BusinessException.class, () -> TaskStatus.validateTransition(terminal, to));
        }
    }

    @Test
    @DisplayName("of() 解析：合法值通过、非法值 409")
    void parse() {
        assertEquals(TaskStatus.OPEN, TaskStatus.of("OPEN"));
        assertEquals(ResultCode.CONFLICT.getCode(),
                assertThrows(BusinessException.class, () -> TaskStatus.of("open")).getCode());
        assertEquals(ResultCode.CONFLICT.getCode(),
                assertThrows(BusinessException.class, () -> TaskStatus.of("NOT_A_STATUS")).getCode());
    }
}
