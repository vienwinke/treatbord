package com.treatbord.module.ai;

import com.sun.net.httpserver.HttpServer;
import com.treatbord.security.JwtUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **端到端**验收：小程序 →(HTTP + JWT)→ Java 代理 → 边车 SSE → 逐帧回传。
 *
 * 为什么必须有它：这条链上已经出过两个真 bug ——
 *   ① 内部 JWT 的 jti 与 Java 侧黑名单键对不上（登出后边车还能用旧 token）；
 *   ② 边车投影出金额（Decimal）时 SSE 序列化中途炸掉，而上游只看到"边车不可达"。
 * 两者都只有把整条链跑起来才会暴露，而此前只有 AiSseFramer / AiTokenService
 * 这类**局部**单测，链路级别的行为无人守。
 *
 * 做法：用 JDK 自带的 {@code com.sun.net.httpserver.HttpServer} 起一个假边车
 * （不引第三方 mock 依赖），真实 Tomcat + 真实鉴权拦截器 + 真实异步 SSE。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AiSseProxyE2ETest {

    /** 内部 JWT 密钥（HS256 要求 ≥32 字节），与边车侧约定同一个值 */
    private static final String TEST_SECRET = "test-sidecar-secret-0123456789-abcdefghij";

    private static HttpServer sidecar;

    /** true = 假边车"还在算"，用于验证 Java 侧的超时降级 */
    private static volatile boolean slowMode = false;

    /**
     * true = 假边车**先发响应头与一帧、然后卡住**。
     *
     * 这模拟的是真实场景里最难判的那种：边车已经 200 开始回帧，只是在算（冷启动 + LLM 5~10s）。
     * 与 slowMode 的区别很关键 —— slowMode 是"发头之前就睡"（超时从 send 抛出，能拿到
     * HttpTimeoutException → 504）；本模式超时发生在**读 body** 阶段，JDK 抛的是
     * `IOException: closed`，若不特殊处理会被归到 502「连接被中断」。实测真链路就踩到这个。
     */
    private static volatile boolean stallMode = false;

    @DynamicPropertySource
    static void sidecarProperties(DynamicPropertyRegistry registry) throws IOException {
        sidecar = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        sidecar.createContext("/v1/ai/chat", AiSseProxyE2ETest::handleChat);
        sidecar.setExecutor(Executors.newFixedThreadPool(4));
        sidecar.start();
        registry.add("treatbord.ai.sidecar.base-url",
                () -> "http://127.0.0.1:" + sidecar.getAddress().getPort());
        registry.add("treatbord.ai.sidecar.jwt-secret", () -> TEST_SECRET);
        // 读超时 = timeout-ms + 2000（见 AiSidecarClient），所以 800ms 会让慢响应在 ~2.8s 被判超时
        registry.add("treatbord.ai.sidecar.timeout-ms", () -> 800);
    }

    @AfterAll
    static void stopSidecar() {
        if (sidecar != null) {
            sidecar.stop(0);
        }
    }

    @Autowired private JwtUtil jwtUtil;

    @LocalServerPort private int port;

    // ---------------------------------------------------------------- 假边车
    private static void handleChat(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            if (stallMode) {
                // 先发头 + 一帧（chunked），再卡住且**不写终止帧** → 模拟"边车还在算"
                byte[] head = "event: meta\ndata: {\"trace_id\":\"t-stall\"}\n\n"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
                exchange.sendResponseHeaders(200, 0);          // 0 = chunked，长度未知
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(head);
                    out.flush();
                    Thread.sleep(6000);                        // 远超读超时（800 + 2000 = 2800）
                }
                return;
            }
            if (slowMode) {
                Thread.sleep(4000);      // 超过 Java 侧读超时（800 + 2000）
            }
            byte[] body = ("event: meta\ndata: {\"trace_id\":\"t-e2e\",\"model\":\"fake\"}\n\n"
                    + "event: scope\ndata: {\"scope\":\"SELF\",\"allowed\":true}\n\n"
                    + "event: delta\ndata: {\"text\":\"你好\"}\n\n"
                    + "event: delta\ndata: {\"text\":\"，这是假边车\"}\n\n"
                    + "event: done\ndata: {\"elapsed_ms\":12,\"tokens\":3,\"cost_yuan\":0.0001}\n\n")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }

    // ---------------------------------------------------------------- 调用
    private HttpResponse<String> callChat() throws Exception {
        String token = jwtUtil.generate(1L, 0, 0)[0];
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/ai/chat"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(25))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"session_id\":\"s1\",\"question\":\"我接了几个任务\","
                                + "\"client_msg_id\":\"m1\"}"))
                .build();
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static List<String> eventNames(String sse) {
        List<String> names = new ArrayList<>();
        for (String line : sse.split("\n")) {
            if (line.startsWith("event:")) {
                names.add(line.substring("event:".length()).trim());
            }
        }
        return names;
    }

    // ---------------------------------------------------------------- 用例
    @Test
    @DisplayName("端到端：帧序与内容逐帧透传（meta→scope→delta×2→done），不被改写也不被合并")
    void proxiesFramesEndToEnd() throws Exception {
        slowMode = false;
        HttpResponse<String> response = callChat();

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertEquals(List.of("meta", "scope", "delta", "delta", "done"), eventNames(body),
                "SSE 帧名与顺序必须原样透传，实际响应：\n" + body);
        assertTrue(body.contains("这是假边车"), "delta 的 data 不应被改写：\n" + body);
        assertTrue(body.contains("\"cost_yuan\":0.0001"), "done 的字段应完整保留：\n" + body);
    }

    @Test
    @DisplayName("流已开始后卡住：必须按超时处理（SIDECAR_TIMEOUT），不能退化成通用 502")
    void stalledStreamIsReportedAsTimeoutNotGenericError() throws Exception {
        stallMode = true;
        try {
            HttpResponse<String> response = callChat();
            assertEquals(200, response.statusCode());
            String body = response.body();
            assertTrue(body.contains("SIDECAR_TIMEOUT"),
                    "边车已回帧后卡住 = 它还在算，应按超时上报（504 语义）。实际响应：\n" + body);
            assertFalse(body.contains("SIDECAR_ERROR"),
                    "不得退化成 502「连接被中断、多半不是网络问题」—— 那句话会把排查方向带偏"
                            + "（边车日志是干净的）。实际响应：\n" + body);
        } finally {
            stallMode = false;
        }
    }

    @Test
    @DisplayName("边车超时：给 error 帧 + SIDECAR_TIMEOUT，HTTP 仍是 200（不是 500）")
    void sidecarTimeoutDegradesHonestly() throws Exception {
        slowMode = true;
        try {
            HttpResponse<String> response = callChat();
            assertEquals(200, response.statusCode(),
                    "SSE 已建立后不能改状态码；故障必须以 error 帧表达，前端才只有一套渲染逻辑");
            String body = response.body();
            assertEquals(List.of("error"), eventNames(body), "超时应只发 error 帧：\n" + body);
            assertTrue(body.contains("SIDECAR_TIMEOUT"),
                    "超时错误码应为 SIDECAR_TIMEOUT（≠ 500，也 ≠ 通用 SIDECAR_ERROR）：\n" + body);
        } finally {
            slowMode = false;
        }
    }
}
