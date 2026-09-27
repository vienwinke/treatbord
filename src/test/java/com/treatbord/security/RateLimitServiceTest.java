package com.treatbord.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 限流器（Lua 原子版）测试：阈值生效、超限拒绝、key 一定带 TTL（不会出现无 TTL 的脏 key）。
 */
@SpringBootTest
@ActiveProfiles("test")
class RateLimitServiceTest {

    private static final String SCOPE = "ittest";

    @Autowired private RateLimitService rateLimitService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM app_config WHERE config_key = ?", SCOPE + ".rate.limit.per.minute");
        Set<String> keys = redisTemplate.keys("ratelimit:" + SCOPE + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("阈值内放行、超限拒绝；计数 key 带 TTL（Lua 原子设置）")
    void luaScriptSetsTtlAtomically() {
        jdbcTemplate.update("INSERT INTO app_config (config_key, config_value) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value)",
                SCOPE + ".rate.limit.per.minute", "2");

        String ip = "10.9.9.9";
        assertTrue(rateLimitService.tryAcquire(SCOPE, ip), "第 1 次放行");
        assertTrue(rateLimitService.tryAcquire(SCOPE, ip), "第 2 次放行（=阈值）");
        assertFalse(rateLimitService.tryAcquire(SCOPE, ip), "第 3 次应超限");

        Set<String> keys = redisTemplate.keys("ratelimit:" + SCOPE + ":*");
        assertTrue(keys != null && !keys.isEmpty(), "应写入计数 key");
        for (String key : keys) {
            Long ttl = redisTemplate.getExpire(key);
            assertTrue(ttl != null && ttl > 0, "key 必须有 TTL（Lua 原子设置），实际=" + ttl + " key=" + key);
        }
    }

    @Test
    @DisplayName("未配置阈值时使用默认值 60，不会误伤")
    void defaultLimitApplies() {
        String ip = "10.8.8.8";
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimitService.tryAcquire("itscope-nodefault", ip), "默认阈值下前 5 次应放行");
        }
        Set<String> keys = redisTemplate.keys("ratelimit:itscope-nodefault:*");
        if (keys != null) {
            redisTemplate.delete(keys);
        }
    }
}
