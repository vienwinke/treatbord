package com.treatbord.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与校验（HS256，载荷含 userId/jti/role/status）。
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties properties;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 token。
     *
     * @param userId 用户 id
     * @param role   角色（0=USER 1=ADMIN）
     * @param status 账号状态（0=正常 1=封禁）
     * @return [token, jti]
     */
    public String[] generate(Long userId, Integer role, Integer status) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getExpireSeconds() * 1000);
        String token = Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .id(jti)
                .claim("userId", userId)
                .claim("role", role)
                .claim("status", status)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key())
                .compact();
        return new String[]{token, jti};
    }

    /**
     * 解析并校验 token，返回 Claims；非法/过期抛 JwtException。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 判断 token 是否合法（含过期校验） */
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long expireSeconds() {
        return properties.getExpireSeconds();
    }
}