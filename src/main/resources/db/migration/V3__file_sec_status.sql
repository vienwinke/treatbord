-- ============================================================
-- V3: file 表新增内容安全状态列（mediaCheckAsync 异步检测回填）
-- 0=待检测 1=通过 2=违规
-- ============================================================

ALTER TABLE `file`
  ADD COLUMN `sec_status` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '内容安全状态 0=待检测 1=通过 2=违规' AFTER `uploader_id`;