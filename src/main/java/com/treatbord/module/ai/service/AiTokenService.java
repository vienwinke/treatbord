package com.treatbord.module.ai.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 签发调用 AI 边车的**内部 JWT**（契约 §2.2）。
 *
 * 三个必须守住的点：
 * 1. `sub` 只能是服务端解析出的 user_id（来自登录态），请求体里没有该字段；
 * 2. 有效期 ≤ 5 分钟（`exp`），配合边车的 jti 黑名单做到"降权即时生效"；
 * 3. 密钥未配置时**直接拒绝**（fail-closed）——绝不签发一个无签名/空密钥的 token。
 */
@Service
public class AiTokenService {

    /** 契约 §2.2：aud 固定为 ai-sidecar，边车会校验 */
    private static final String AUDIENCE = "ai-sidecar";
    private static final long DEFAULT_TTL_SECONDS = 300;

    private final SecretKey key;
    private final long ttlSeconds;

    public AiTokenService(
            @Value("${treatbord.ai.sidecar.jwt-secret:}") String secret,
            @Value("${treatbord.ai.sidecar.token-ttl-seconds:300}") long ttlSeconds) {
        this.ttlSeconds = ttlSeconds <= 0 || ttlSeconds > DEFAULT_TTL_SECONDS
                ? DEFAULT_TTL_SECONDS : ttlSeconds;
        if (secret == null || secret.isBlank()) {
            this.key = null;                     // fail-closed：见 issue()
        } else if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            // HS256 要求 ≥256 bit；给明确报错，别等到运行期抛 WeakKeyException
            throw new IllegalArgumentException(
                    "treatbord.ai.sidecar.jwt-secret 至少需要 32 字节（HS256 要求 ≥256 bit）");
        } else {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 是否已配置密钥（未配置时调用方应直接返回 503，而不是裸调边车） */
    public boolean configured() {
        return key != null;
    }

    /** 简化入口：不指定 jti 时自行生成（仅用于不关心吊销的场景/测试） */
    public String issue(long userId, String role) {
        return issue(userId, role, null);
    }

    /**
     * 为某个用户签发内部 JWT。
     *
     * @param userId 服务端登录态里的 user_id（**不是**客户端传的）
     * @param role   USER / OPERATOR / ADMIN
     * @param jti    **沿用调用方当前会话的 jti**（`UserContext.get().getJti()`）。
     *               这一点很关键：treatbord 的登出/封禁/注销会把*会话 jti* 写进 Redis
     *               `token:blacklist:<jti>`，边车查的是同一个键 —— 只有 jti 一致，
     *               "踢人下线"才会对 AI 问答即时生效；每次生成新 jti 的话，
     *               登出后边车仍能用旧 token 继续查（最长 5 分钟窗口）。
     */
    public String issue(long userId, String role, String jti) {
        if (key == null) {
            throw new IllegalStateException(
                    "未配置 treatbord.ai.sidecar.jwt-secret，拒绝签发内部 JWT（fail-closed）");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .id(jti == null || jti.isBlank() ? UUID.randomUUID().toString() : jti)   // 沿用会话 jti：吊销才对边车生效
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
