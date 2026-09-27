package com.treatbord.security;

import com.treatbord.module.config.service.AppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Redis 固定窗口限流（**Lua 原子版**）。
 *
 * <p>为什么改：原实现是 `INCR` + `EXPIRE` 两次网络往返——若两步之间发生异常/进程崩溃，
 * key 会没有 TTL（脏 key，永久驻留）。改为 Lua 后，"计数 + 首次设置 TTL"在 Redis 内部**原子执行**，
 * 不存在中间态。
 *
 * <p>保留固定窗口算法语义（阈值仍取 app_config 的 {@code {scope}.rate.limit.per.minute}）；
 * 固定窗口固有的"窗口边界双倍突发"由滑动窗口方案（ZSet）另行解决。
 *
 * <p>降级策略：Redis 异常时放行，避免限流器本身拖垮主链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final int DEFAULT_LIMIT = 60;

    /** 窗口 61s（比 1 分钟多 1s，覆盖窗口切换边界） */
    private static final long WINDOW_MS = 61_000L;

    /** 计数 + 首次设置过期时间，原子执行 */
    private static final RedisScript<Long> INCR_WITH_TTL = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if tonumber(c) == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                    + "return c",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AppConfigService appConfigService;

    /**
     * 尝试获取一次配额。
     *
     * @param scope      业务场景（login/claim/submit/upload/taskcreate/report/review/notify/fileview）
     * @param identifier 限流维度（IP）
     * @return true=放行，false=超限
     */
    public boolean tryAcquire(String scope, String identifier) {
        int limit = appConfigService.getInt(scope + ".rate.limit.per.minute", DEFAULT_LIMIT);
        long minute = System.currentTimeMillis() / 60000;
        String key = "ratelimit:" + scope + ":" + identifier + ":" + minute;
        try {
            Long count = redisTemplate.execute(INCR_WITH_TTL, List.of(key), String.valueOf(WINDOW_MS));
            return count == null || count <= limit;
        } catch (Exception e) {
            log.warn("限流器异常，放行 scope={}", scope, e);
            return true;
        }
    }
}
