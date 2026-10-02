package com.treatbord.module.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treatbord.common.Result;
import com.treatbord.common.ResultCode;
import com.treatbord.module.ai.dto.AiChatRequest;
import com.treatbord.module.ai.dto.AiFeedbackRequest;
import com.treatbord.module.ai.service.AiSseCollector;
import com.treatbord.module.ai.service.AiSidecarClient;
import com.treatbord.module.ai.service.AiTokenService;
import com.treatbord.security.UserContext;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 小程序 → AI 边车的问答入口（SSE 代理）。
 *
 * 身份：`UserContext`（拦截器写入的登录态）→ 内部 JWT → 边车；请求体里**没有** user_id。
 * 流式：边车 SSE 逐帧转成 `SseEmitter` 事件，不缓冲整段。
 * 失败：以 `event: error` 帧形式收尾（与边车一致），而不是抛 500 —— 前端只需一套渲染逻辑。
 *
 * ⚠️ 小程序侧：`wx.request` 不支持标准 SSE 消费，正式接入请用 WSS 或
 * `enableChunked` 自切片（见 TBagent 仓库 docs/treatbord嵌入-Java侧接入要点.md §1）。
 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private static final Logger log = LoggerFactory.getLogger(AiChatController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiSidecarClient sidecar;
    private final AiTokenService tokenService;
    private final ExecutorService executor;
    private final long timeoutMs;

    public AiChatController(AiSidecarClient sidecar,
                            AiTokenService tokenService,
                            @Qualifier("aiSidecarExecutor") ExecutorService executor,
                            @Value("${treatbord.ai.sidecar.timeout-ms:8000}") long timeoutMs) {
        this.sidecar = sidecar;
        this.tokenService = tokenService;
        this.executor = executor;
        this.timeoutMs = timeoutMs;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AiChatRequest request) {
        SseEmitter emitter = new SseEmitter(timeoutMs + 3000);   // 略大于边车预算

        UserContext.CurrentUser me = UserContext.get();
        if (me == null) {
            fail(emitter, "UNAUTHENTICATED", "未登录");
            return emitter;
        }
        if (!tokenService.configured()) {
            // fail-closed：宁可不可用，也不裸调边车（那等于绕过身份）
            fail(emitter, "AUTH_NOT_CONFIGURED",
                    "未配置 treatbord.ai.sidecar.jwt-secret，无法签发内部 JWT");
            return emitter;
        }

        long userId = me.getUserId();
        String role = AiSidecarClient.roleOf(me.getRole());
        executor.execute(() -> {
            SseFrameForwarder forwarder = new SseFrameForwarder(emitter);
            try {
                // 沿用当前会话 jti：登出/封禁后边车即时拒绝（与 Java 侧同一黑名单键）
                sidecar.streamChat(userId, role, me.getJti(), request, forwarder::accept);
                forwarder.flush();
                emitter.complete();
            } catch (AiSidecarClient.SidecarHttpException e) {
                log.warn("[ai] 边车调用失败 user={} status={} msg={}", userId, e.getStatus(),
                        e.getMessage());
                // 按故障类别给准确的 code：503=边车没起 · 504=边车还在算 · 502=边车处理中出错
                String code = switch (e.getStatus()) {
                    case 503 -> "SIDECAR_UNAVAILABLE";
                    case 504 -> "SIDECAR_TIMEOUT";
                    case 401, 403 -> "UNAUTHENTICATED";
                    default -> "SIDECAR_ERROR";
                };
                forwarder.errorFrame(code, e.getMessage());
                emitter.complete();
            } catch (RuntimeException e) {
                log.error("[ai] 未知异常 user={}", userId, e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void fail(SseEmitter emitter, String code, String message) {
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data("{\"code\":\"" + code + "\",\"message\":\"" + message
                            + "\",\"retryable\":false}"));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    // ---------------------------------------------------------------- 非流式兜底
    /**
     * 非流式问答：给不支持 `enableChunked` 的基础库兜底，也便于自动化测试/curl。
     * 内部仍是同一条链路（范围判定 → NL2SQL → 脱敏 → 转述），只是把流收集成一个结果。
     */
    @PostMapping("/ask")
    public Result<Map<String, Object>> ask(@Valid @RequestBody AiChatRequest request) {
        UserContext.CurrentUser me = UserContext.get();
        if (me == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        if (!tokenService.configured()) {
            return Result.error(503, "未配置 treatbord.ai.sidecar.jwt-secret");
        }
        try {
            AiSseCollector collected = sidecar.ask(me.getUserId(),
                    AiSidecarClient.roleOf(me.getRole()), me.getJti(), request);
            if (collected.failed()) {
                return Result.error(502, collected.errorMessage());
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("answer", collected.answer());
            out.put("tables", collected.tables());
            if (collected.done() != null) {
                out.put("route", collected.done().path("route").asText(null));
                out.put("denied", collected.done().path("denied").asBoolean(false));
                out.put("elapsed_ms", collected.done().path("elapsed_ms").asInt(0));
                out.put("tokens", collected.done().path("tokens").asInt(0));
                out.put("cost_yuan", collected.done().path("cost_yuan").asDouble(0.0));
            }
            return Result.ok(out);
        } catch (AiSidecarClient.SidecarHttpException e) {
            log.warn("[ai] /ask 调边车失败 user={} status={}", me.getUserId(), e.getStatus());
            int status = e.getStatus() >= 400 ? e.getStatus() : 502;
            return Result.error(status, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 会话（透传边车）
    @GetMapping("/sessions")
    public Result<JsonNode> sessions() {
        return proxy("GET", "/v1/ai/sessions", null);
    }

    @GetMapping("/sessions/{id}/messages")
    public Result<JsonNode> messages(@PathVariable long id) {
        return proxy("GET", "/v1/ai/sessions/" + id + "/messages", null);
    }

    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable long id) {
        Result<JsonNode> r = proxy("DELETE", "/v1/ai/sessions/" + id, null);
        return r.getCode() == 0 ? Result.ok() : Result.error(r.getCode(), r.getMessage());
    }

    @PostMapping("/feedback")
    public Result<Void> feedback(@Valid @RequestBody AiFeedbackRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message_id", request.messageId());
        body.put("rating", request.rating());
        body.put("comment", request.comment());
        Result<JsonNode> r = proxy("POST", "/v1/ai/feedback", body);
        return r.getCode() == 0 ? Result.ok() : Result.error(r.getCode(), r.getMessage());
    }

    /** 透传边车 JSON 接口：身份一律来自登录态，客户端传什么都不影响 user_id */
    private Result<JsonNode> proxy(String method, String path, Object body) {
        UserContext.CurrentUser me = UserContext.get();
        if (me == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        if (!tokenService.configured()) {
            return Result.error(503, "未配置 treatbord.ai.sidecar.jwt-secret");
        }
        try {
            String text = sidecar.callJson(method, path, body, me.getUserId(),
                    AiSidecarClient.roleOf(me.getRole()), me.getJti());
            return Result.ok(text == null || text.isEmpty() ? null : MAPPER.readTree(text));
        } catch (AiSidecarClient.SidecarHttpException e) {
            // 保留边车的**客户端错误**语义（401/403/404/400），不要一律折成 502：
            // 否则"这条会话/回答不是你的"到了前端就变成"服务异常"，排查方向全错。
            int status = switch (e.getStatus()) {
                case 400, 401, 403, 404 -> e.getStatus();
                default -> e.getStatus() == 0 ? 502 : 502;
            };
            String message = e.getStatus() == 0 ? "AI 服务暂时不可用" : e.getMessage();
            return Result.error(status, message);
        } catch (Exception e) {
            log.warn("[ai] 解析边车响应失败 path={}", path, e);
            return Result.error(502, "回答解析失败");
        }
    }

    /** 把边车的 SSE 行重新组装成帧（`event:` + `data:` + 空行）后转发 */
    static final class SseFrameForwarder {

        private final SseEmitter emitter;
        private String event;
        private final StringBuilder data = new StringBuilder();

        SseFrameForwarder(SseEmitter emitter) {
            this.emitter = emitter;
        }

        void accept(String line) {
            if (line.isEmpty()) {
                flush();
            } else if (line.startsWith(":")) {
                // 注释帧（心跳/connected）：忽略即可，SseEmitter 自己会保活
            } else if (line.startsWith("event:")) {
                event = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).trim());
            }
        }

        void flush() {
            if (event == null && data.length() == 0) {
                return;
            }
            SseEmitter.SseEventBuilder builder = SseEmitter.event();
            if (event != null && !event.isBlank()) {
                builder.name(event);
            }
            try {
                emitter.send(builder.data(data.length() == 0 ? "{}" : data.toString()));
            } catch (Exception e) {
                throw new IllegalStateException("SSE 发送失败（客户端可能已断开）", e);
            }
            event = null;
            data.setLength(0);
        }

        void errorFrame(String code, String message) {
            event = "error";
            data.setLength(0);
            data.append("{\"code\":\"").append(code).append("\",\"message\":\"")
                    .append(message == null ? "" : message.replace("\"", "'"))
                    .append("\",\"retryable\":true}");
            flush();
        }
    }
}
