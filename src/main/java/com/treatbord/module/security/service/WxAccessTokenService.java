package com.treatbord.module.security.service;

import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.auth.config.WxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * 微信 access_token 管理（内容安全接口依赖）。
 * 缓存于 Redis，TTL 略短于官方 7200s，避免边界失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxAccessTokenService {

    private static final String CACHE_KEY = "wx:access_token";
    private static final Duration TTL = Duration.ofSeconds(7000);
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";

    private final WxProperties wxProperties;
    private final StringRedisTemplate redisTemplate;
    private final RestClient.Builder restClientBuilder;

    /**
     * 获取有效 access_token（优先缓存）。
     *
     * @throws BusinessException 微信未配置或获取失败
     */
    public String getAccessToken() {
        String cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        if (!wxProperties.isEnabled() || wxProperties.getAppid() == null || wxProperties.getAppid().isBlank()
                || wxProperties.getSecret() == null || wxProperties.getSecret().isBlank()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "微信配置缺失，无法获取 access_token");
        }

        Map<?, ?> resp = restClientBuilder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(TOKEN_URL)
                        .queryParam("grant_type", "client_credential")
                        .queryParam("appid", wxProperties.getAppid())
                        .queryParam("secret", wxProperties.getSecret())
                        .build())
                .retrieve()
                .body(Map.class);

        if (resp == null || resp.get("access_token") == null) {
            log.error("获取微信 access_token 失败: errcode={} errmsg={}",
                    resp == null ? null : resp.get("errcode"),
                    resp == null ? null : resp.get("errmsg"));
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "获取微信 access_token 失败");
        }
        String token = String.valueOf(resp.get("access_token"));
        redisTemplate.opsForValue().set(CACHE_KEY, token, TTL);
        return token;
    }
}