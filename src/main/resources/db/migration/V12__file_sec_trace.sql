-- ============================================================
-- V12: file 表补齐内容安全异步结果回填所需的两列
--
-- 背景（修 bug）：mediaCheckAsync 是异步接口，结果通过**微信消息推送**回调返回，
-- 关联键是提交时拿到的 trace_id。此前 checkImage 只把这个 trace_id 打了日志、
-- 没有落库，回调即便接进来也无法定位到是哪张图 ——
-- 于是 sec_status 永远停在 0（待检测），FileService/SubmissionService 里
-- sec_status=2 的两处违规过滤成了永不触发的死代码，违规图片永远可见。
-- ============================================================

ALTER TABLE `file`
  ADD COLUMN `sec_trace_id`   VARCHAR(64) DEFAULT NULL
      COMMENT 'mediaCheckAsync 返回的 trace_id（回调关联键）' AFTER `sec_status`,
  ADD COLUMN `sec_checked_at` DATETIME    DEFAULT NULL
      COMMENT '内容安全结果回填时间' AFTER `sec_trace_id`,
  ADD KEY `idx_file_sec_trace` (`sec_trace_id`);
