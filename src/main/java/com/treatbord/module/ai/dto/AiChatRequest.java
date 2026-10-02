package com.treatbord.module.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 小程序 → treatbord 的问答请求。
 *
 * ⚠️ **这里没有、也不允许有 user_id 字段**：身份只能来自服务端登录态
 * （见 docs/treatbord嵌入-接口契约.md §2.2 —— "绝不接受任何客户端自带的身份字段"）。
 * 一旦加上该字段并被下游使用，行级隔离就形同虚设。
 */
public record AiChatRequest(
        // ★ 键名必须是 snake_case：L2 契约（docs/treatbord嵌入-接口契约.md §2.1）与边车
        //   Pydantic 模型用的都是 session_id / client_msg_id。少了这个注解，
        //   Java 会把 sessionId 原样发出去，边车直接 422（实测踩到过）。
        @JsonProperty("session_id")
        @NotBlank(message = "session_id 不能为空")
        String sessionId,

        @JsonProperty("question")
        @NotBlank(message = "question 不能为空")
        @Size(max = 2000, message = "问题过长")
        String question,

        @JsonProperty("client_msg_id")
        String clientMsgId
) {
}
