package com.treatbord.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 测试：签发/解析回读、篡改无效、换密钥无效、签发者校验、过期无效。
 */
class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-key-0123456789-abcdefghij"; // >= 32 bytes

    private JwtUtil util(String secret, long expireSeconds) {
        JwtProperties props = new JwtProperties();
        props.setSecret(secret);
        props.setIssuer("treatbord");
        props.setExpireSeconds(expireSeconds);
        return new JwtUtil(props);
    }

    @Test
    @DisplayName("签发后可解析出 userId/role/status/jti")
    void generateThenParse() {
        JwtUtil jwt = util(SECRET, 60);
        String[] tokenAndJti = jwt.generate(42L, 1, 0);

        Claims claims = jwt.parse(tokenAndJti[0]);
        assertEquals("42", claims.getSubject());
        assertEquals(42L, ((Number) claims.get("userId")).longValue());
        assertEquals(1, ((Number) claims.get("role")).intValue());
        assertEquals(0, ((Number) claims.get("status")).intValue());
        assertEquals(tokenAndJti[1], claims.getId(), "jti 应一致（登出黑名单靠它）");
        assertEquals("treatbord", claims.getIssuer());
        assertTrue(jwt.isValid(tokenAndJti[0]));
    }

    @Test
    @DisplayName("每次签发 jti 不同（同一用户多端会话可分别注销）")
    void jtiIsUniquePerToken() {
        JwtUtil jwt = util(SECRET, 60);
        assertFalse(jwt.generate(1L, 0, 0)[1].equals(jwt.generate(1L, 0, 0)[1]));
    }

    @Test
    @DisplayName("篡改签名后无效")
    void tamperedTokenIsInvalid() {
        JwtUtil jwt = util(SECRET, 60);
        String token = jwt.generate(1L, 0, 0)[0];
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertFalse(jwt.isValid(tampered));
        assertFalse(jwt.isValid("not-a-jwt"));
    }

    @Test
    @DisplayName("换密钥签发的 token 不被接受")
    void tokenSignedWithAnotherKeyIsInvalid() {
        String token = util(SECRET, 60).generate(1L, 0, 0)[0];
        assertFalse(util("another-secret-key-0123456789-abcdefghij", 60).isValid(token));
    }

    @Test
    @DisplayName("签发者不匹配时无效")
    void wrongIssuerIsInvalid() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        props.setIssuer("someone-else");
        props.setExpireSeconds(60);
        String token = new JwtUtil(props).generate(1L, 0, 0)[0];
        assertFalse(util(SECRET, 60).isValid(token));
    }

    @Test
    @DisplayName("过期 token 无效")
    void expiredTokenIsInvalid() throws InterruptedException {
        JwtUtil shortLived = util(SECRET, 1);
        String token = shortLived.generate(1L, 0, 0)[0];
        assertTrue(shortLived.isValid(token));
        Thread.sleep(1200);
        assertFalse(shortLived.isValid(token), "过期后必须无效");
    }

    @Test
    @DisplayName("expireSeconds 透出配置值（登录响应 expiresIn 用）")
    void expireSecondsExposed() {
        assertEquals(604800L, util(SECRET, 604800).expireSeconds());
    }
}
