package com.treatbord.security;

import com.treatbord.module.config.service.AppConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录失败锁定（防密码暴力破解）。
 *
 * <p>维度设计（重要取舍）：
 * <ul>
 *   <li><b>IP 维度：硬锁</b>——失败达到阈值后，该 IP 在锁定期内拒绝登录；</li>
 *   <li><b>账号维度：只累计、不硬锁</b>——否则"知道用户名就能把别人账号锁住"（拒绝服务）。</li>
 * </ul>
 * 首次失败时设置 TTL（固定窗口，到期自动清零）；登录成功即清零。
 *
 * <p>阈值来自 app_config：login.fail.threshold（默认 5）、login.fail.lock.minutes（默认 15）。
 * 降级：Redis 异常一律视为"未锁定"，不阻塞正常登录。
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String IP_PREFIX = "login:fail:ip:";
    private static final String USER_PREFIX = "login:fail:user:";
    private static final String THRESHOLD_KEY = "login.fail.threshold";
    private static final String LOCK_MINUTES_KEY = "login.fail.lock.minutes";

    private final StringRedisTemplate redisTemplate;
    private final AppConfigService appConfigService;

    public boolean isLocked(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        try {
            String v = redisTemplate.opsForValue().get(IP_PREFIX + ip);
            return v != null && Integer.parseInt(v) >= threshold();
        } catch (Exception e) {
            return false;
        }
    }

    public void recordFailure(String username, String ip) {
        try {
            if (ip != null && !ip.isBlank()) {
                increment(IP_PREFIX + ip);
            }
            if (username != null && !username.isBlank()) {
                increment(USER_PREFIX + username);
            }
        } catch (Exception ignored) {
            // 计数失败不影响登录主流程
        }
    }

    public void clear(String username, String ip) {
        try {
            if (ip != null && !ip.isBlank()) {
                redisTemplate.delete(IP_PREFIX + ip);
            }
            if (username != null && !username.isBlank()) {
                redisTemplate.delete(USER_PREFIX + username);
            }
        } catch (Exception ignored) {
        }
    }

    /** 账号维度失败次数（只读，供审计/风控观察） */
    public int accountFailures(String username) {
        try {
            String v = redisTemplate.opsForValue().get(USER_PREFIX + username);
            return v == null ? 0 : Integer.parseInt(v);
        } catch (Exception e) {
            return 0;
        }
    }

    private void increment(String key) {
        Long c = redisTemplate.opsForValue().increment(key);
        if (c != null && c == 1L) {
            redisTemplate.expire(key, Duration.ofMinutes(lockMinutes()));
        }
    }

    private int threshold() {
        return appConfigService.getInt(THRESHOLD_KEY, 5);
    }

    private long lockMinutes() {
        return appConfigService.getInt(LOCK_MINUTES_KEY, 15);
    }
}
