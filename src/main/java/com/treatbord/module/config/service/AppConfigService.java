package com.treatbord.module.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.treatbord.module.config.entity.AppConfig;
import com.treatbord.module.config.mapper.AppConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 运行期配置读取（app_config 表）。
 */
@Service
@RequiredArgsConstructor
public class AppConfigService {

    /**
     * 本地缓存 TTL。app_config 是"运行期可调"的，但**不能每次判定都查一次库**：
     * 限流拦截器对每个 /api/** 与 /files/** 请求都要读阈值，
     * 等于给每个请求白加一次 DB 往返（登录失败计数同理）。
     * 取 10s：调参最迟 10 秒生效，够用且把读放大消掉。
     */
    private static final long CACHE_TTL_MS = 10_000;

    private final AppConfigMapper appConfigMapper;

    private final java.util.concurrent.ConcurrentHashMap<String, Cached> cache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 缓存项；value 为 null 表示"库里没有这个键"，同样缓存，避免反复查不存在的键 */
    private record Cached(String value, long expireAt) {
    }

    /** 读取字符串配置；不存在返回默认值。命中本地缓存（TTL 10s） */
    public String get(String key, String defaultValue) {
        long now = System.currentTimeMillis();
        Cached hit = cache.get(key);
        if (hit != null && hit.expireAt() > now) {
            return hit.value() == null ? defaultValue : hit.value();
        }
        AppConfig c = appConfigMapper.selectOne(new LambdaQueryWrapper<AppConfig>()
                .eq(AppConfig::getConfigKey, key));
        String value = c == null ? null : c.getConfigValue();
        cache.put(key, new Cached(value, now + CACHE_TTL_MS));
        return value == null ? defaultValue : value;
    }

    /** 显式失效（写入配置后调用可让改动立即生效，不必等 TTL） */
    public void invalidate(String key) {
        cache.remove(key);
    }

    /** 全量失效 */
    public void invalidateAll() {
        cache.clear();
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBool(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }
}