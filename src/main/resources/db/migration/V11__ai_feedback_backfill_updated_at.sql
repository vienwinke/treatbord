-- ============================================================================
-- V11 · 修正 V10 的历史行假阳性（updated_at 对齐 created_at）
--
-- ⚠️ 本文件由 TBagent 仓库的 scripts/export_ai_migration.py **自动生成**，请勿手改：
--     权威定义在 TBagent/sql/ai_tables.sql（含授权模板与运维说明），改那边后重新生成。
--
-- Flyway 用独立迁移账号（MYSQL_MIGRATE_USER / db_migrate，含 DDL），
-- 因此本迁移会在应用启动时自动建表，无需人工执行 mysql 命令。
-- ============================================================================

UPDATE ai_feedback SET updated_at = created_at WHERE updated_at > created_at;
