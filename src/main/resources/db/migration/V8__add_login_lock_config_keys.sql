-- ============================================================
-- V8: 登录失败锁定阈值（LoginAttemptService 读取）
-- 幂等：ON DUPLICATE KEY UPDATE 不覆盖已有值
-- ============================================================

INSERT INTO `app_config` (`config_key`, `config_value`)
VALUES
  ('login.fail.threshold',    '5'),    -- IP 维度失败阈值
  ('login.fail.lock.minutes', '15')    -- 锁定时长（分钟）
ON DUPLICATE KEY UPDATE `config_value` = `config_value`;
