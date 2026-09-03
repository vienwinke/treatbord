-- ============================================================
-- Treatbord 初始化种子数据
-- 依据 docs/DB_DESIGN.md
--
-- 仅初始化 app_config 配置项与可选 admin 账号。
-- 重复执行需自行保证幂等（多数用 INSERT IGNORE / ON DUPLICATE KEY）。
-- ============================================================

USE `treatbord`;

-- ---------- 1. app_config 运行期配置项 ----------
-- 键唯一，重复执行用 ON DUPLICATE KEY 幂等更新（不覆盖已有）
INSERT INTO `app_config` (`config_key`, `config_value`)
VALUES
  ('login.rate.limit.per.minute',   '30'),    -- 登录接口限流（同 IP+用户 / 分钟）
  ('claim.rate.limit.per.minute',   '20'),    -- 接取接口限流
  ('submit.rate.limit.per.minute',  '20'),    -- 提交接口限流
  ('upload.rate.limit.per.minute',  '20'),    -- 上传接口限流
  ('review.timeout.hours',          '48'),    -- 审核超时自动通过窗口（小时）
  ('auto.approve.enabled',          'true'),  -- 审核超时自动通过开关
  ('claim.timeout.hours',           '72'),    -- 接取后未提交自动取消窗口（小时）
  ('file.max.size.mb',              '5'),     -- 单文件大小上限 MB
  ('file.allowed.ext',              'jpg,png,webp'), -- 文件类型白名单
  ('content.security.enabled',      'true'),  -- 内容安全检测开关
  ('page.size.max',                 '20'),    -- 分页上限
  ('task.cache.ttl.seconds',        '300'),   -- 任务缓存 TTL
  ('credit.penalty.overdue',        '10')     -- 接取超时未提交扣除信用分
ON DUPLICATE KEY UPDATE `config_value` = `config_value`;

-- ---------- 2. admin 账号 ----------
-- ⚠️ 生产环境禁止用固定 openid 建 admin。
--    admin 应通过业务系统按微信授权登录后，再由管理员在后台提权（role=1）。
--    此处仅为本地开发提供一个占位示例，可删除。
-- INSERT INTO `user` (`openid`, `nickname`, `role`, `status`)
-- VALUES ('DEV_ADMIN_PLACEHOLDER', '管理员', 1, 0);

-- 结束
