package com.treatbord.module.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 内部 JWT 的签发契约（不依赖 Spring 上下文与数据库）：
 * sub / role / aud / jti / exp 全部按 docs/treatbord嵌入-接口契约.md §2.2 校验。
 */
class AiTokenServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef"; // 32 字节

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .requireAudience("ai-sidecar")
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Test
    void issuesTokenWithContractClaims() {
        AiTokenService service = new AiTokenService(SECRET, 300);
        String token = service.issue(42L, "USER");
        Claims claims = parse(token);

        assertEquals("42", claims.getSubject(), "sub 必须是 user_id");
        assertEquals("USER", claims.get("role", String.class));
        assertEquals("ai-sidecar", claims.getAudience().iterator().next());
        assertNotNull(claims.getId(), "jti 必须存在（审计与吊销都靠它）");
        assertTrue(claims.getExpiration().after(new Date()), "exp 必须在未来");
    }

    @Test
    void lifetimeNeverExceedsFiveMinutes() {
        // 即便传了更大的值也会被夹到 300 秒（契约 §2.2：exp ≤ 5 分钟）
        AiTokenService service = new AiTokenService(SECRET, 86_400);
        Claims claims = parse(service.issue(7L, "ADMIN"));
        long seconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertTrue(seconds <= 300, "有效期必须 ≤300s，实际 " + seconds);
    }

    @Test
    void jtiIsCarriedFromCallerSession() {
        // ★ 跨仓库集成要点：内部 JWT 的 jti 必须沿用调用方会话的 jti，
        //   否则 treatbord 登出写的 token:blacklist:<会话jti> 对边车不生效。
        AiTokenService service = new AiTokenService(SECRET, 300);
        Claims claims = parse(service.issue(7L, "USER", "session-jti-123"));
        assertEquals("session-jti-123", claims.getId());

        // 不传时自行生成（不能为空，否则吊销与审计都失去抓手）
        assertNotNull(parse(service.issue(7L, "USER", null)).getId());
    }

    @Test
    void tokenIsNotSignedWithAnotherSecret() {
        String token = new AiTokenService(SECRET, 300).issue(7L, "USER");
        String otherSecret = "ffffffffffffffffffffffffffffffff";
        assertThrows(RuntimeException.class, () -> Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(otherSecret.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token));
    }

    @Test
    void refusesToIssueWhenSecretMissing() {
        AiTokenService service = new AiTokenService("", 300);
        assertFalse(service.configured(), "未配置密钥时 configured() 必须为 false");
        assertThrows(IllegalStateException.class, () -> service.issue(7L, "USER"));
    }

    @Test
    void rejectsTooShortSecret() {
        assertThrows(IllegalArgumentException.class, () -> new AiTokenService("short", 300));
    }
}
