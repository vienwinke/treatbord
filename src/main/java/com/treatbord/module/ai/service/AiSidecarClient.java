package com.treatbord.module.ai.service;

import com.treatbord.module.ai.dto.AiChatRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 调 AI 边车并把 SSE 流**逐行转发**给调用方（契约 §2、docs/treatbord嵌入-Java侧接入要点.md）。
 *
 * 两个容易写错的地方：
 * 1. **不要缓冲整段**：一旦等边车把整段返回完再转发，流式就白做了（首字延迟等于整段延迟）；
 * 2. **trace 必须贯穿**：Java 生成 `X-Trace-Id` → 边车写进审计表，线上排障才不用两边对数。
 */
@Service
public class AiSidecarClient {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final AiTokenService tokenService;
    private final RestClient client;
    private final String baseUrl;
    private final long timeoutMs;

    public AiSidecarClient(AiTokenService tokenService,
                           @Value("${treatbord.ai.sidecar.base-url:http://127.0.0.1:8080}") String baseUrl,
                           @Value("${treatbord.ai.sidecar.timeout-ms:8000}") long timeoutMs) {
        this.tokenService = tokenService;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeoutMs = timeoutMs;
        // ★ 必须显式指定 HTTP/1.1：JDK HttpClient 默认会对明文端口尝试 **h2c 升级**
        //   （发 `Upgrade: h2c` + `HTTP2-Settings` + `Transfer-encoding: chunked`）。
        //   边车是 uvicorn，不支持该升级 —— 结果是请求体被丢掉，边车报
        //   `422 {'loc':['body'],'msg':'Field required'}`，而用 curl/Postman 测又完全正常，
        //   极难定位（实测抓包才看到）。锁 HTTP/1.1 后一切正常。
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofMillis(timeoutMs + 2000));   // ≥ 边车端到端预算
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    /** 角色映射：treatbord 目前只有 0=普通用户 / 1=管理员（OPERATOR 档位预留） */
    public static String roleOf(Integer roleCode) {
        return Integer.valueOf(1).equals(roleCode) ? "ADMIN" : "USER";
    }

    /**
     * 转发一次问答。回调按行收到边车的 SSE 文本（`event:` / `data:` / 空行）；
     * 非 2xx 一律抛异常，由上层决定怎么呈现（401/429/503 的语义见契约 §2.4）。
     */
    public void streamChat(long userId, String role, String sessionJti, AiChatRequest request,
                           Consumer<String> onLine) {
        String token = tokenService.issue(userId, role, sessionJti);
        String traceId = UUID.randomUUID().toString().replace("-", "");
        try {
            client.post()
                    .uri(baseUrl + "/v1/ai/chat")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Trace-Id", traceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((req, resp) -> {
                        if (resp.getStatusCode().isError()) {
                            // 把边车返回的**原因**也带上：只有状态码（比如 422）时，
                            // 排查只能靠猜（这次就是靠它才定位到键名不符）。
                            String detail = "";
                            try (java.io.InputStream err = resp.getBody()) {
                                detail = new String(err.readAllBytes(), StandardCharsets.UTF_8);
                            } catch (java.io.IOException ignored) {
                                // 读不到就算了，不影响错误语义
                            }
                            throw new SidecarHttpException(resp.getStatusCode().value(),
                                    "边车返回 " + resp.getStatusCode()
                                            + (detail.isEmpty() ? "" : "：" + detail));
                        }
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                onLine.accept(line);
                            }
                        }
                        return null;
                    });
        } catch (SidecarHttpException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw new SidecarHttpException(e.getStatusCode().value(), "边车调用失败: " + e.getMessage());
        } catch (RuntimeException e) {
            throw classify(e, "chat");
        }
    }

    /**
     * 把底层异常翻译成**可区分的**故障（别再一律"边车不可达"）。
     *
     * 为什么重要：流中途的服务端异常（例如边车序列化崩溃把连接掐了）以前也被报成
     * "边车不可达: I/O error ... closed"，排查方向被完全带偏 —— 实测靠翻边车日志才定位到
     * `Decimal is not JSON serializable`。现在分成三类，一眼能看出该查哪边：
     *  · 连接失败   → 503：边车没起 / 端口不通（查部署）
     *  · 读超时     → 504：边车还在算（查预算与模型）
     *  · 流被中断   → 502：边车**处理中出错**（查边车日志，通常不是网络）
     */
    // 参数用 Throwable：底层异常是 IOException 家族（ConnectException/HttpTimeoutException），
    // 若声明成 RuntimeException，javac 会因"类型可证明不相交"直接拒绝 instanceof 判断。
    static SidecarHttpException classify(Throwable e, String where) {
        // ⚠️ 必须遍历**因果链**：Spring 把连接失败包成 ResourceAccessException，
        //    真正的 ConnectException 在 cause 上；只看最外层会漏判（实测）。
        StringBuilder chain = new StringBuilder();
        for (Throwable t = e; t != null && chain.length() < 1000; t = t.getCause()) {
            chain.append(t.getClass().getName()).append(' ')
                 .append(String.valueOf(t.getMessage())).append(" | ");
        }
        String lower = chain.toString().toLowerCase();
        boolean connectFail = chain.indexOf("java.net.ConnectException") >= 0
                || chain.indexOf("HttpConnectTimeoutException") >= 0
                || lower.contains("connection refused");
        if (connectFail) {
            return new SidecarHttpException(503, "AI 服务未启动或不可达（连接失败）：" + e.getMessage());
        }
        if (chain.indexOf("HttpTimeoutException") >= 0 || lower.contains("timed out")
                || lower.contains("timeout")) {
            return new SidecarHttpException(504, "AI 服务响应超时（" + where + "）：" + e.getMessage());
        }
        if (lower.contains("closed") || lower.contains("goaway") || lower.contains("unexpected end")
                || lower.contains("chunked")) {
            return new SidecarHttpException(502,
                    "AI 服务处理异常（连接被中断，多半不是网络问题，请看边车日志）：" + e.getMessage());
        }
        return new SidecarHttpException(502, "AI 服务调用异常（" + where + "）：" + e.getMessage());
    }

    /**
     * 调边车的普通 JSON 接口（会话列表/消息/反馈）—— 会话归属与鉴权都在边车侧，
     * Java 只做透传（内部 JWT 由本类签发，客户端无法伪造身份）。
     *
     * @return 边车响应体原文（调用方自行解析）；非 2xx 抛 SidecarHttpException
     */
    public String callJson(String method, String path, Object body, long userId, String role,
                           String sessionJti) {
        String token = tokenService.issue(userId, role, sessionJti);
        org.springframework.web.client.RestClient.RequestBodySpec spec =
                client.method(org.springframework.http.HttpMethod.valueOf(method))
                        .uri(baseUrl + path)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Trace-Id", UUID.randomUUID().toString().replace("-", ""));
        if (body != null) {
            // ⚠️ `spec.body(...)` **返回的是新对象**，必须接住：不接住等于没挂上 body，
            // 边车会以 422 "Field required (loc=body)" 拒绝（实测踩到）。
            spec = (org.springframework.web.client.RestClient.RequestBodySpec)
                    spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        try {
            return spec.exchange((req, resp) -> {
                String text;
                try (java.io.InputStream in = resp.getBody()) {
                    text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (java.io.IOException e) {
                    throw new SidecarHttpException(0, "读取边车响应失败: " + e.getMessage());
                }
                if (resp.getStatusCode().isError()) {
                    // 边车错误体是 {"code":..., "message":...}；直接整段塞进 Result.message 的话，
                    // 前端会看到一坨 JSON。这里取出内层 message（取不到才退回原文）。
                    throw new SidecarHttpException(resp.getStatusCode().value(), extractMessage(text));
                }
                return text;
            });
        } catch (RestClientResponseException e) {
            throw new SidecarHttpException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (SidecarHttpException e) {
            throw e;
        } catch (RuntimeException e) {
            throw classify(e, "json");
        }
    }

    /**
     * 非流式问答：把同一套 SSE 收集成一次结果。
     * 用途：小程序基础库不支持 `enableChunked` 时的兜底，也方便 curl/自动化测试。
     */
    public AiSseCollector ask(long userId, String role, String sessionJti, AiChatRequest request) {
        AiSseCollector collector = new AiSseCollector();
        streamChat(userId, role, sessionJti, request, collector);
        collector.accept("");                    // 收尾：吐出残留帧
        return collector;
    }

    /** 从边车错误体里取可读信息：{"code":..,"message":..} → message */
    private static String extractMessage(String body) {
        if (body == null || body.isBlank()) {
            return "边车返回空错误体";
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(body);
            String message = node.path("message").asText("");
            if (!message.isEmpty()) {
                return message;
            }
            String code = node.path("code").asText("");
            return code.isEmpty() ? body : code;
        } catch (Exception e) {
            return body;
        }
    }

    /** 边车侧错误：status 0 表示连不上（超时/连接失败） */
    public static class SidecarHttpException extends RuntimeException {
        private final int status;

        public SidecarHttpException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }
}
