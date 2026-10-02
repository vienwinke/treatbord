package com.treatbord.module.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * 回答反馈（👍/👎）。rating 只接受 1 / -1，由边车再校验一次。
 */
public record AiFeedbackRequest(
        @JsonProperty("message_id")
        @NotNull(message = "message_id 不能为空")
        Long messageId,

        @NotNull(message = "rating 不能为空")
        Integer rating,

        String comment
) {
}
