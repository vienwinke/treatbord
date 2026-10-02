-- ============================================================================
-- V10 · ai_feedback 加 updated_at（评价被改过可见）
--
-- ⚠️ 本文件由 TBagent 仓库的 scripts/export_ai_migration.py **自动生成**，请勿手改：
--     权威定义在 TBagent/sql/ai_tables.sql（含授权模板与运维说明），改那边后重新生成。
--
-- Flyway 用独立迁移账号（MYSQL_MIGRATE_USER / db_migrate，含 DDL），
-- 因此本迁移会在应用启动时自动建表，无需人工执行 mysql 命令。
-- ============================================================================

ALTER TABLE ai_feedback
  ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
      ON UPDATE CURRENT_TIMESTAMP COMMENT '最近一次修改时间（> created_at 说明改过评价）';
