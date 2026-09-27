-- ============================================================
-- V6: 定时任务分批扫描的索引补齐
-- 背景：接取超时扫描按 (status, create_time)、审核超时扫描按 (status, submitted_at) 过滤，
--       原索引 idx_claim_status_revdl(status, review_deadline) 无法覆盖这两个时间列 → Using where 全区间扫描。
-- 收益：两个扫描从"按 status 扫全区间再过滤"变为"直接按时间范围取批"，分批 LIMIT 才能提前终止。
-- 代价：task_claim 增加两个二级索引（该表写入量低，可接受）。
-- ============================================================

ALTER TABLE `task_claim`
  ADD INDEX `idx_claim_status_ctime` (`status`, `create_time`);

ALTER TABLE `task_claim`
  ADD INDEX `idx_claim_status_submitted` (`status`, `submitted_at`);
