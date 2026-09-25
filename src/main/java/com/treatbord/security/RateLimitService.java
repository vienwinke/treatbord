package com.treatbord.security;

import com.treatbord.module.config.service.AppConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis 固定窗口限流：按 {scope}:{identifier}:{分钟} 计数，阈值取自 app_config（如 login.rate.limit.per.minute）。
 * 降级策略：Redis 异常时放行（不阻塞主链路），避免限流器本身引发故障。
 */
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final int DEFAULT_LIMIT = 60;
    private final StringRedisTemplate redisTemplate;
    private final AppConfigService appConfigService;

    /**
     * 尝试获取一次配额。
     *
     * @param scope      业务场景（login/claim/submit/upload），对应 app_config 阈值前缀
     * @param identifier 限流维度（IP 或 userId）
     * @return true=放行，false=超限
     */
    public boolean tryAcquire(String scope, String identifier) {
        int limit = appConfigService.getInt(scope + ".rate.limit.per.minute", DEFAULT_LIMIT);
        long minute = System.currentTimeMillis() / 60000;
        String key = "ratelimit:" + scope + ":" + identifier + ":" + minute;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(61));
            }
            return count == null || count <= limit;
        } catch (Exception e) {
            // 限流器异常不阻塞业务
            return true;
        }
    }
}
