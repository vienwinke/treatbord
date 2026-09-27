-- ============================================================
-- V7: 补齐新增限流场景的阈值配置（RateLimitInterceptor 覆盖面扩展）
-- 幂等：ON DUPLICATE KEY UPDATE 不覆盖已有值
-- ============================================================

INSERT INTO `app_config` (`config_key`, `config_value`)
VALUES
  ('taskcreate.rate.limit.per.minute', '10'),   -- 发布任务
  ('report.rate.limit.per.minute',     '5'),    -- 提交举报
  ('review.rate.limit.per.minute',     '20'),   -- 提交互评
  ('notify.rate.limit.per.minute',     '60')    -- 通知查询/已读
ON DUPLICATE KEY UPDATE `config_value` = `config_value`;
