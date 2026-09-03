package com.treatbord.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * JWT 黑名单：登出/封禁后将 jti 加入 Redis，实现"踢人下线"即时生效
 * （docs/SECURITY_REVIEW.md A2）。
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "token:blacklist:";

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;

    /** 将 jti 加入黑名单，TTL=token 剩余有效期 */
    public void blacklist(String jti) {
        redisTemplate.opsForValue()
                .set(KEY_PREFIX + jti, "1", Duration.ofSeconds(jwtUtil.expireSeconds()));
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }
}