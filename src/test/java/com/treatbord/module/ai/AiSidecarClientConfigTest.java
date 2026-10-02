package com.treatbord.module.ai;

import com.treatbord.module.ai.service.AiSidecarClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 边车地址配置守卫。
 *
 * 守的是一个**真实存在过的缺陷**：`treatbord.ai.sidecar.base-url` 的默认值是
 * `http://127.0.0.1:8080`，而 treatbord 自己的 `server.port` 也是 8080 ——
 * 默认配置下请求会打回自己（`/v1/ai/chat` 404 → 被包成 502），而且**不会报端口冲突**。
 * 所以这里要求：自指必须在**启动期**被拒绝，并且报错要点明正确写法。
 */
class AiSidecarClientConfigTest {

    private static AiSidecarClient client(String baseUrl, int serverPort) {
        return new AiSidecarClient(null, baseUrl, 8000L, serverPort);
    }

    @Test
    @DisplayName("base-url 指向自己 -> 启动期直接拒绝，而不是运行期静默 502")
    void rejectsSelfReferencingBaseUrl() {
        for (String selfRef : new String[]{
                "http://127.0.0.1:8080", "http://localhost:8080", "http://[::1]:8080"}) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> client(selfRef, 8080), "应拒绝自指配置：" + selfRef);
            assertTrue(e.getMessage().contains("指向了 treatbord 自己"), e.getMessage());
            assertTrue(e.getMessage().contains("8081"), "报错要给出正确例子：" + e.getMessage());
        }
    }

    @Test
    @DisplayName("同机换端口 / docker 服务名都放行")
    void acceptsOtherPortOrServiceName() {
        assertTrue(client("http://127.0.0.1:8081", 8080).configured());
        assertTrue(client("http://sidecar:8080", 8080).configured());
        assertTrue(client("http://sidecar", 8080).configured(), "服务名不带端口应走默认 80");
    }

    @Test
    @DisplayName("未配置 base-url = 未启用（fail-closed），而不是默认指到某处")
    void blankBaseUrlMeansNotConfigured() {
        assertFalse(client("", 8080).configured());
        assertFalse(client("   ", 8080).configured());
        assertFalse(client(null, 8080).configured());
    }

    @Test
    @DisplayName("尾部斜杠规整；非法 URL 也在启动期报错")
    void normalisesTrailingSlashAndRejectsGarbage() {
        assertTrue(client("http://127.0.0.1:8081/", 8080).configured());
        assertThrows(IllegalStateException.class, () -> client("这不是 URL", 8080));
    }

    @Test
    @DisplayName("容器内用服务名时，端口与服务端口相同也不算自指（域名不是回环）")
    void serviceNameIsNeverSelfReference() {
        assertTrue(client("http://treatbord_app:8080", 8080).configured());
    }
}
