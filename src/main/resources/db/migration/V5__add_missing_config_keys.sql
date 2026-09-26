-- ============================================================
-- V5: 补齐「代码在读取、种子数据却缺失」的配置键 + 直读文件限流阈值
-- 幂等：ON DUPLICATE KEY UPDATE 不覆盖已有值
-- ============================================================

INSERT INTO `app_config` (`config_key`, `config_value`)
VALUES
  ('credit.claim.threshold',         '60'),   -- ClaimService: 接取所需最低信用分
  ('credit.penalty.reject',          '5'),    -- ReviewService: 审核驳回扣分
  ('fileview.rate.limit.per.minute', '120')   -- RateLimitInterceptor: /files/** 直读限流
ON DUPLICATE KEY UPDATE `config_value` = `config_value`;
