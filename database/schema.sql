-- ============================================================
-- Treatbord 数据库 schema
-- 依据 docs/DB_DESIGN.md
--
-- ⚠️ 本脚本含 DROP DATABASE IF EXISTS（重建），
--    仅限开发/测试环境。生产请使用增量迁移脚本（Flyway/Liquibase）。
--
-- 执行身份：管理员（需具备建库、建表、建账号权限）
-- ============================================================

-- ---------- 1. 建库 ----------
CREATE DATABASE IF NOT EXISTS `treatbord`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `treatbord`;

-- ---------- 2. 应用账号（最小权限） ----------
-- 密码占位，请通过环境变量/加密注入，禁止明文入 git。
CREATE USER IF NOT EXISTS 'app_user'@'%' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';
-- 生产建议限定主机： 'app_user'@'10.0.0.%'（内网）而非 '%'
GRANT SELECT, INSERT, UPDATE, DELETE ON `treatbord`.* TO 'app_user'@'%';
-- 不授予 DDL 权限

-- ---------- 3. 建表 ----------

-- 3.1 用户
CREATE TABLE IF NOT EXISTS `user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `openid`        VARCHAR(64)  NOT NULL COMMENT '微信 openid；注销时匿名化为 DEL_<uuid>',
  `unionid`       VARCHAR(64)  DEFAULT NULL COMMENT '预留',
  `nickname`      VARCHAR(30)  DEFAULT NULL COMMENT '昵称，需内容安全检测',
  `avatar`        VARCHAR(255) DEFAULT NULL,
  `credit_score`  INT          NOT NULL DEFAULT 100 COMMENT '信用分',
  `role`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '0=USER 1=ADMIN',
  `status`        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '0=正常 1=封禁',
  `register_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户';

-- 3.2 任务
CREATE TABLE IF NOT EXISTS `task` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `publisher_id`   BIGINT        NOT NULL COMMENT '发布者 user_id',
  `title`          VARCHAR(50)   NOT NULL COMMENT '标题 ≤50',
  `description`    VARCHAR(2000) DEFAULT NULL COMMENT '描述 ≤2000',
  `reward`         DECIMAL(10,2) NOT NULL COMMENT '报酬',
  `quota`          INT           NOT NULL COMMENT '名额',
  `claimed_count`  INT           NOT NULL DEFAULT 0 COMMENT '已接取数',
  `claim_deadline` DATETIME      NOT NULL COMMENT '接取截止',
  `deadline`       DATETIME      NOT NULL COMMENT '完成截止',
  `status`         VARCHAR(20)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/IN_PROGRESS/REVIEWING/SETTLED/EXPIRED/CANCELLED',
  `version`        INT           NOT NULL DEFAULT 0 COMMENT '乐观锁',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`        TINYINT(1)    NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_task_status_claimdead` (`status`, `claim_deadline`),
  KEY `idx_task_status_ctime` (`status`, `create_time`),
  KEY `idx_task_publisher` (`publisher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务';

-- 3.3 接取记录
CREATE TABLE IF NOT EXISTS `task_claim` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `task_id`         BIGINT       NOT NULL,
  `user_id`         BIGINT       NOT NULL COMMENT '接取者',
  `status`          VARCHAR(20)  NOT NULL DEFAULT 'CLAIMED' COMMENT 'CLAIMED/SUBMITTED/APPROVED/REJECTED/CANCELLED',
  `reward`          DECIMAL(10,2) NOT NULL COMMENT '结算快照',
  `submitted_at`    DATETIME     DEFAULT NULL,
  `reviewed_at`     DATETIME     DEFAULT NULL,
  `review_deadline` DATETIME     DEFAULT NULL COMMENT '提交后48h审核截止',
  `review_note`     VARCHAR(500) DEFAULT NULL,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_claim_task_user` (`task_id`, `user_id`),
  KEY `idx_claim_user_ctime` (`user_id`, `create_time`),
  KEY `idx_claim_status_revdl` (`status`, `review_deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务接取';

-- 3.4 提交凭证
CREATE TABLE IF NOT EXISTS `task_submission` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `claim_id`    BIGINT       NOT NULL,
  `content`     VARCHAR(2000) DEFAULT NULL COMMENT '文字凭证 ≤2000',
  `file_ids`    JSON         DEFAULT NULL COMMENT '凭证图片 file.id 数组',
  `submit_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_submission_claim` (`claim_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提交凭证';

-- 3.5 文件
CREATE TABLE IF NOT EXISTS `file` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `storage_key` VARCHAR(255) NOT NULL COMMENT 'biz/{type}/{yyyyMM}/{uuid}.ext',
  `url`         VARCHAR(255) NOT NULL,
  `size`        BIGINT       NOT NULL,
  `mime`        VARCHAR(100) DEFAULT NULL,
  `uploader_id` BIGINT       NOT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_key` (`storage_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件';

-- 3.6 通知
CREATE TABLE IF NOT EXISTS `notification` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL,
  `type`        VARCHAR(30)  NOT NULL,
  `title`       VARCHAR(100) DEFAULT NULL,
  `content`     VARCHAR(500) DEFAULT NULL,
  `biz_id`      BIGINT       DEFAULT NULL,
  `is_read`     TINYINT(1)   NOT NULL DEFAULT 0,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_noti_user_read_ctime` (`user_id`, `is_read`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知';

-- 3.7 互评
CREATE TABLE IF NOT EXISTS `review` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `claim_id`     BIGINT       NOT NULL,
  `from_user_id` BIGINT       NOT NULL,
  `to_user_id`   BIGINT       NOT NULL,
  `score`        TINYINT      NOT NULL COMMENT '1-5',
  `content`      VARCHAR(500) DEFAULT NULL,
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review` (`claim_id`, `from_user_id`, `to_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='互评';

-- 3.8 举报
CREATE TABLE IF NOT EXISTS `report` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `reporter_id` BIGINT       NOT NULL,
  `target_type` VARCHAR(30)  NOT NULL COMMENT 'task/claim/review/user',
  `target_id`   BIGINT       NOT NULL,
  `reason`      VARCHAR(500) DEFAULT NULL,
  `status`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '0=待处理 1=已处理 2=驳回',
  `handler_id`  BIGINT       DEFAULT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_report_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='举报';

-- 3.9 结算（预留）
CREATE TABLE IF NOT EXISTS `settlement` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `claim_id`    BIGINT        NOT NULL,
  `task_id`     BIGINT        NOT NULL,
  `user_id`     BIGINT        NOT NULL,
  `amount`      DECIMAL(10,2) NOT NULL,
  `status`      TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '0=待结算 1=已结算',
  `settle_time` DATETIME      DEFAULT NULL,
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT(1)    NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settle_claim` (`claim_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算（预留）';

-- 3.10 任务状态审计
CREATE TABLE IF NOT EXISTS `task_status_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `task_id`     BIGINT       NOT NULL,
  `from_status` VARCHAR(20)  DEFAULT NULL,
  `to_status`   VARCHAR(20)  NOT NULL,
  `operator_id` BIGINT       DEFAULT NULL,
  `reason`      VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tasklog_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务状态日志';

-- 3.11 接取状态审计
CREATE TABLE IF NOT EXISTS `claim_status_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `claim_id`    BIGINT       NOT NULL,
  `from_status` VARCHAR(20)  DEFAULT NULL,
  `to_status`   VARCHAR(20)  NOT NULL,
  `operator_id` BIGINT       DEFAULT NULL,
  `reason`      VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_claimlog_claim` (`claim_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='接取状态日志';

-- 3.12 关键操作审计
CREATE TABLE IF NOT EXISTS `audit_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       DEFAULT NULL,
  `action`      VARCHAR(50)  NOT NULL,
  `target_type` VARCHAR(30)  DEFAULT NULL,
  `target_id`   BIGINT       DEFAULT NULL,
  `detail`      VARCHAR(500) DEFAULT NULL,
  `ip`          VARCHAR(45)  DEFAULT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_user` (`user_id`),
  KEY `idx_audit_action_ctime` (`action`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='关键操作审计';

-- 3.13 登录记录
CREATE TABLE IF NOT EXISTS `login_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       DEFAULT NULL,
  `ip`          VARCHAR(45)  DEFAULT NULL,
  `user_agent`  VARCHAR(255) DEFAULT NULL,
  `success`     TINYINT(1)   NOT NULL DEFAULT 0,
  `fail_reason` VARCHAR(100) DEFAULT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_login_user` (`user_id`),
  KEY `idx_login_ctime` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录记录';

-- 3.14 配置（无 deleted，配置不逻辑删）
CREATE TABLE IF NOT EXISTS `app_config` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `config_key`   VARCHAR(50)  NOT NULL,
  `config_value` VARCHAR(500) DEFAULT NULL,
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运行期配置';

-- ---------- 4. 重建账号授权（保证重复执行幂等） ----------
-- 若需限定主机（生产），将 '@'%'' 改为 '@'内网IP段'' 并重跑授权
GRANT SELECT, INSERT, UPDATE, DELETE ON `treatbord`.* TO 'app_user'@'%';
FLUSH PRIVILEGES;

-- 结束
