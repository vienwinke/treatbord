package com.treatbord.module.task.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treatbord.common.PageResult;
import com.treatbord.module.config.service.AppConfigService;
import com.treatbord.module.task.dto.TaskVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 任务缓存（Cache-Aside 模式）。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>防穿透</b>：查不到的任务写入"空值标记"（短 TTL 60s），避免不存在的 id 反复打库；</li>
 *   <li><b>防雪崩</b>：TTL = 配置值 + 0~60s 随机抖动，避免大批 key 同时失效；</li>
 *   <li><b>失效策略</b>：详情按 key 删除；列表用"版本号"（{@code cache:task:listver}）——
 *       任何写操作只做一次 INCR，旧版本 key 自然不可达并随 TTL 过期，避免 SCAN 全量删除；</li>
 *   <li><b>每用户字段不缓存</b>：{@code claimedByMe} 依赖当前登录用户，命中缓存后单独补算。</li>
 * </ul>
 *
 * <p>降级：Redis 异常一律"未命中/跳过写缓存"，不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCacheService {

    private static final String DETAIL_PREFIX = "cache:task:detail:";
    private static final String LIST_PREFIX = "cache:task:list:";
    private static final String LIST_VERSION_KEY = "cache:task:listver";
    private static final String NULL_MARKER = "{\"exists\":false}";
    private static final int DEFAULT_TTL_SECONDS = 300;
    private static final int JITTER_MAX_SECONDS = 60;
    private static final int NULL_TTL_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;
    private final AppConfigService appConfigService;
    private final ObjectMapper objectMapper;

    // ---------- 详情 ----------

    /** 返回 empty 表示"未缓存"；exists=false 表示"缓存了'不存在'"（防穿透） */
    public Optional<CachedDetail> getDetail(Long taskId) {
        try {
            String json = redisTemplate.opsForValue().get(DETAIL_PREFIX + taskId);
            if (json == null) {
                return Optional.empty();
            }
            if (NULL_MARKER.equals(json)) {
                log.debug("[CACHE] 详情命中(空值) taskId={}", taskId);
                return Optional.of(new CachedDetail(false, null));
            }
            log.debug("[CACHE] 详情命中 taskId={}", taskId);
            return Optional.of(objectMapper.readValue(json, CachedDetail.class));
        } catch (Exception e) {
            log.warn("读取任务详情缓存失败 taskId={}", taskId, e);
            return Optional.empty();
        }
    }

    public void putDetail(Long taskId, TaskVO vo) {
        try {
            redisTemplate.opsForValue().set(DETAIL_PREFIX + taskId,
                    objectMapper.writeValueAsString(new CachedDetail(true, vo)), ttl());
            log.debug("[CACHE] 详情回填 taskId={} ttl={}s", taskId, ttl().getSeconds());
        } catch (Exception e) {
            log.warn("写入任务详情缓存失败 taskId={}", taskId, e);
        }
    }

    /** 空值缓存：防穿透 */
    public void putDetailAbsent(Long taskId) {
        try {
            redisTemplate.opsForValue().set(DETAIL_PREFIX + taskId, NULL_MARKER,
                    Duration.ofSeconds(NULL_TTL_SECONDS));
        } catch (Exception e) {
            log.warn("写入空值缓存失败 taskId={}", taskId, e);
        }
    }

    // ---------- 列表 ----------

    /** 列表缓存 key 带版本号：写操作 INCR 版本号后，旧 key 自动不可达 */
    public String listKey(String status, String keyword, long page, long pageSize) {
        return LIST_PREFIX + "v" + listVersion() + ":" + safe(status) + ":" + safe(keyword)
                + ":" + page + ":" + pageSize;
    }

    public Optional<PageResult<TaskVO>> getList(String cacheKey) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json == null) {
                return Optional.empty();
            }
            log.debug("[CACHE] 列表命中 key={}", cacheKey);
            return Optional.of(objectMapper.readValue(json, new TypeReference<PageResult<TaskVO>>() {
            }));
        } catch (Exception e) {
            log.warn("读取任务列表缓存失败 key={}", cacheKey, e);
            return Optional.empty();
        }
    }

    public void putList(String cacheKey, PageResult<TaskVO> value) {
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(value), ttl());
            log.debug("[CACHE] 列表回填 key={} ttl={}s", cacheKey, ttl().getSeconds());
        } catch (Exception e) {
            log.warn("写入任务列表缓存失败 key={}", cacheKey, e);
        }
    }

    // ---------- 失效 ----------

    /** 某任务发生变化：删除其详情缓存 + 列表版本号 +1 */
    public void onTaskChanged(Long taskId) {
        try {
            if (taskId != null) {
                redisTemplate.delete(DETAIL_PREFIX + taskId);
            }
            redisTemplate.opsForValue().increment(LIST_VERSION_KEY);
        } catch (Exception e) {
            log.warn("失效任务缓存失败 taskId={}", taskId, e);
        }
    }

    /** 批量任务（如定时过期扫描）只需让列表缓存整体失效 */
    public void onListChanged() {
        try {
            redisTemplate.opsForValue().increment(LIST_VERSION_KEY);
        } catch (Exception e) {
            log.warn("失效任务列表缓存失败", e);
        }
    }

    // ---------- 内部 ----------

    private long listVersion() {
        try {
            String v = redisTemplate.opsForValue().get(LIST_VERSION_KEY);
            return v == null ? 0L : Long.parseLong(v);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Duration ttl() {
        int base = appConfigService.getInt("task.cache.ttl.seconds", DEFAULT_TTL_SECONDS);
        int jitter = ThreadLocalRandom.current().nextInt(JITTER_MAX_SECONDS + 1);
        return Duration.ofSeconds(base + jitter);
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "_" : s.replace(':', '_');
    }

    /** 详情缓存载体（exists=false 表示空值缓存） */
    @Data
    public static class CachedDetail {
        private boolean exists;
        private TaskVO vo;

        public CachedDetail() {
        }

        public CachedDetail(boolean exists, TaskVO vo) {
            this.exists = exists;
            this.vo = vo;
        }
    }
}
