package com.treatbord.module.file.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 签名器单测（纯逻辑，无 Spring 容器）：
 * 签名可验证、篡改路径/签名无效、过期无效、换密钥无效。
 */
class FileUrlSignerTest {

    private static final String KEY = "biz/submission/202609/abc123.png";

    private FileUrlSigner signer(String secret, String required, String ttl) {
        MockEnvironment env = new MockEnvironment()
                .withProperty("treatbord.storage.sign-secret", secret)
                .withProperty("treatbord.storage.signed-url.required", required)
                .withProperty("treatbord.storage.signed-url.ttl-seconds", ttl)
                .withProperty("treatbord.storage.base-url", "http://example.com/files");
        return new FileUrlSigner(env);
    }

    @Test
    @DisplayName("签名 URL 形如 ?exp=..&sig=..，且可校验通过")
    void signThenVerify() {
        FileUrlSigner s = signer("unit-test-secret", "true", "300");
        String url = s.signedUrl(KEY);
        assertNotNull(url);
        assertTrue(url.contains("?exp="));
        assertTrue(url.contains("&sig="));
        assertTrue(url.startsWith("http://example.com/files/" + KEY));

        long exp = Long.parseLong(url.substring(url.indexOf("?exp=") + 5, url.indexOf("&sig=")));
        String sig = url.substring(url.indexOf("&sig=") + 5);
        assertTrue(s.verify(KEY, String.valueOf(exp), sig), "正确签名应校验通过");
    }

    @Test
    @DisplayName("篡改文件路径后签名失效（防越权访问他人文件）")
    void tamperedPathRejected() {
        FileUrlSigner s = signer("unit-test-secret", "true", "300");
        String url = s.signedUrl(KEY);
        long exp = Long.parseLong(url.substring(url.indexOf("?exp=") + 5, url.indexOf("&sig=")));
        String sig = url.substring(url.indexOf("&sig=") + 5);

        assertFalse(s.verify("biz/submission/202609/OTHER.png", String.valueOf(exp), sig),
                "换路径后签名必须失效");
    }

    @Test
    @DisplayName("伪造签名 / 缺失参数 / 时间戳非法一律拒绝")
    void tamperedSignatureRejected() {
        FileUrlSigner s = signer("unit-test-secret", "true", "300");
        long exp = System.currentTimeMillis() / 1000 + 60;
        assertFalse(s.verify(KEY, String.valueOf(exp), "forged-signature"));
        assertFalse(s.verify(KEY, String.valueOf(exp), null));
        assertFalse(s.verify(KEY, null, "sig"));
        assertFalse(s.verify(KEY, "not-a-number", "sig"));
        assertFalse(s.verify(null, String.valueOf(exp), "sig"));
    }

    @Test
    @DisplayName("过期签名失效")
    void expiredRejected() {
        FileUrlSigner s = signer("unit-test-secret", "true", "300");
        long pastExp = System.currentTimeMillis() / 1000 - 10;
        String sig = s.sign(KEY, pastExp);
        assertFalse(s.verify(KEY, String.valueOf(pastExp), sig), "过期签名必须失效");
    }

    @Test
    @DisplayName("换密钥后旧签名失效（可实现全局失效）")
    void anotherSecretRejected() {
        FileUrlSigner a = signer("secret-a", "true", "300");
        FileUrlSigner b = signer("secret-b", "true", "300");
        String url = a.signedUrl(KEY);
        long exp = Long.parseLong(url.substring(url.indexOf("?exp=") + 5, url.indexOf("&sig=")));
        String sig = url.substring(url.indexOf("&sig=") + 5);
        assertTrue(a.verify(KEY, String.valueOf(exp), sig));
        assertFalse(b.verify(KEY, String.valueOf(exp), sig), "换密钥后旧签名必须失效");
    }

    @Test
    @DisplayName("required 开关：dev 默认 false，可用配置覆盖为 true")
    void requiredFlag() {
        assertFalse(signer("s", "false", "300").required());
        assertTrue(signer("s", "true", "300").required());
        assertTrue(signer("s", "true", "60").ttlSeconds() == 60L);
    }
}
