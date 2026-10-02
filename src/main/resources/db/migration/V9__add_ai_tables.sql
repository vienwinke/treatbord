-- ============================================================================
-- V9 · AI 边车所需表（会话 / 消息 / 审计 / 反馈 / 提示词版本）
--
-- ⚠️ 本文件由 TBagent 仓库的 scripts/export_ai_migration.py **自动生成**，请勿手改：
--     权威定义在 TBagent/sql/ai_tables.sql（含授权模板与运维说明），改那边后重新生成。
--
-- Flyway 用独立迁移账号（MYSQL_MIGRATE_USER / db_migrate，含 DDL），
-- 因此本迁移会在应用启动时自动建表，无需人工执行 mysql 命令。
-- ============================================================================

CREATE TABLE IF NOT EXISTS ai_chat_session (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  external_id  VARCHAR(64)  NULL COMMENT 'L1/L2 契约里的字符串 session_id（外部标识）',
  user_id      BIGINT       NOT NULL,
  title        VARCHAR(64)  NOT NULL DEFAULT '',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_external (external_id),
  KEY idx_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 会话（面向用户可见的聊天列表）';

CREATE TABLE IF NOT EXISTS ai_chat_message (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  session_id  BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  role        VARCHAR(16)  NOT NULL,
  content     MEDIUMTEXT   NULL,
  payload     JSON         NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_session (session_id),
  KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 会话消息（含图表/引用 payload）';

CREATE TABLE IF NOT EXISTS ai_query_audit (
  id                 BIGINT        NOT NULL AUTO_INCREMENT,
  trace_id           CHAR(32)      NOT NULL,
  user_id            BIGINT        NOT NULL,
  session_id         BIGINT        NULL,
  question           TEXT          NOT NULL,
  route              VARCHAR(16)   NULL,
  scope              VARCHAR(16)   NULL,
  detected_tables    JSON          NULL,
  generated_sql      TEXT          NULL,
  rewritten_sql      TEXT          NULL,
  policy_version     VARCHAR(16)   NULL,
  verdict            VARCHAR(16)   NULL,
  deny_reason        VARCHAR(64)   NULL,
  row_count          INT           NULL,
  truncated          TINYINT(1)    NOT NULL DEFAULT 0,
  masked_columns     JSON          NULL,
  latency_ms         INT           NULL,
  prompt_tokens      INT           NULL,
  completion_tokens  INT           NULL,
  cost_yuan          DECIMAL(10,6) NULL,
  cache_hit          TINYINT(1)    NOT NULL DEFAULT 0,
  repaired           TINYINT(1)    NOT NULL DEFAULT 0,
  model              VARCHAR(64)   NULL,
  created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, created_at),
  KEY idx_trace (trace_id),
  KEY idx_verdict_time (verdict, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 问答审计（一次问答一行，只增不改）';

CREATE TABLE IF NOT EXISTS ai_feedback (
  message_id  BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  rating      TINYINT      NOT NULL COMMENT '1=赞 / -1=踩',
  comment     VARCHAR(255) NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (message_id),
  KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 回答反馈';

CREATE TABLE IF NOT EXISTS ai_prompt_version (
  name         VARCHAR(32)  NOT NULL,
  version      VARCHAR(16)  NOT NULL,
  content      MEDIUMTEXT   NULL,
  enabled      TINYINT(1)   NOT NULL DEFAULT 0,
  traffic_pct  TINYINT      NOT NULL DEFAULT 0,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (name, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提示词版本（灰度/回滚）';
