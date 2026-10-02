package com.treatbord.module.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/**
 * 故障分类（不依赖 Spring 上下文与网络）：三种故障必须给出**可区分**的结论。
 *
 * 背景：以前一律报"边车不可达"，于是"边车序列化崩溃把连接掐了"也被当成网络问题，
 * 排查方向被完全带偏（实测翻了半天边车日志才定位到 Decimal 序列化）。
 */
class AiSidecarClientTest {

    @Test
    void classifiesConnectionRefusedAsUnavailable() {
        // Spring 会把连接失败包成 ResourceAccessException，真正的 ConnectException 在 cause 上
        ResourceAccessException e = new ResourceAccessException(
                "I/O error on POST request", new java.net.ConnectException("Connection refused"));
        AiSidecarClient.SidecarHttpException out = AiSidecarClient.classify(e, "chat");
        assertEquals(503, out.getStatus());
        assertTrue(out.getMessage().contains("未启动或不可达"), out.getMessage());
    }

    @Test
    void classifiesReadTimeoutAsTimeout() {
        ResourceAccessException e = new ResourceAccessException(
                "I/O error", new java.net.http.HttpTimeoutException("request timed out"));
        assertEquals(504, AiSidecarClient.classify(e, "chat").getStatus());
    }

    @Test
    void classifiesAbortedStreamAsServerErrorNotNetwork() {
        // 这条最关键：流中途被掐 = 服务端处理中出错，**不是**"边车不可达"
        ResourceAccessException e = new ResourceAccessException(
                "I/O error on POST request", new IOException("closed"));
        AiSidecarClient.SidecarHttpException out = AiSidecarClient.classify(e, "chat");
        assertEquals(502, out.getStatus());
        assertTrue(out.getMessage().contains("处理异常"), out.getMessage());
        assertTrue(!out.getMessage().contains("不可达"), "不该再叫不可达：" + out.getMessage());
    }
}
