-- ============================================================
-- V2: 初始化种子数据（app_config 配置项）
-- 幂等：ON DUPLICATE KEY UPDATE 不覆盖已有值
-- ============================================================

INSERT INTO `app_config` (`config_key`, `config_value`)
VALUES
  ('login.rate.limit.per.minute',   '30'),
  ('claim.rate.limit.per.minute',   '20'),
  ('submit.rate.limit.per.minute',  '20'),
  ('upload.rate.limit.per.minute',  '20'),
  ('review.timeout.hours',          '48'),
  ('auto.approve.enabled',          'true'),
  ('claim.timeout.hours',           '72'),
  ('file.max.size.mb',              '5'),
  ('file.allowed.ext',              'jpg,png,webp'),
  ('content.security.enabled',      'true'),
  ('page.size.max',                 '20'),
  ('task.cache.ttl.seconds',        '300'),
  ('credit.penalty.overdue',        '10')
ON DUPLICATE KEY UPDATE `config_value` = `config_value`;