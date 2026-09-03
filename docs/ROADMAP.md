# Treatbord 剩余任务待办清单（Roadmap）

> 依据 `docs/API_DESIGN.md` §13 实现优先级拆解。
> 当前进度：**P0（骨架/认证）+ P1（任务/接取）已完成**。以下为剩余全部任务。
> 状态标记：⬜ 待做 → 🔄 进行中 → ✅ 完成

---

## ✅ 已完成（P0 + P1）

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 | 项目骨架、登录/登出/me/注销、JWT+黑名单、审计、Flyway | ✅ |
| P1 | 任务发布/列表/详情/取消、接取防超卖/取消/我的、状态机 | ✅ |

---

## 🎯 P2：凭证 + 通知 + 审核 + 定时任务（业务完整闭环，**当前优先级**）

### 2.1 凭证模块（submission）✅

- [ ] 实体/Mapper：`task_submission` 表
- [ ] `POST /api/claims/{id}/submit` — 提交完成凭证
  - [ ] CLAIMED → SUBMITTED（CAS，状态机校验）
  - [ ] 写入 submission（content ≤2000 + fileIds[]）
  - [ ] **覆盖提交**：同 claim 再次提交时更新/替换最新一条（评审前）
  - [ ] 更新 claim.submitted_at / review_deadline（+48h）
  - [ ] 内容安全（文本 msgSecCheck + 图片 mediaCheckAsync，开关配置）
  - [ ] 写 claim_status_log + audit_log + 通知发布者
- [ ] `GET /api/claims/{id}` — 查看详情（含最新凭证+文件 URL）
  - [ ] 归属校验：`claim.user_id == 当前用户` 或 `task.publisher_id == 当前用户`
- [ ] 前端重试幂等：重复 submit 返回当前状态不报错

### 2.2 通知模块（notify）✅

- [ ] 实体/Mapper/Service：`notification` 表 + 通用 `notify(userId, type, title, content, bizId)` 方法
- [ ] `GET /api/notifications` — 分页列表（user_id=当前用户，isRead 筛选）
- [ ] `POST /api/notifications/{id}/read` — 标记已读（幂等）
- [ ] `POST /api/notifications/read-all` — 全部已读
- [ ] 触发点接入：接取成功→通知发布者；提交→通知发布者；审核结果→通知接取者（§2.4/§2.3 联用）

### 2.3 审核模块（review/claim review）✅

- [ ] `POST /api/claims/{id}/review` — 审核凭证
  - [ ] 归属校验：`task.publisher_id == 当前用户`（**IDOR 重点**）
  - [ ] SUBMITTED → APPROVED / REJECTED（CAS + 状态机）
  - [ ] 更新 reviewed_at / review_note
  - [ ] 写 claim_status_log + audit_log + 通知接取者
  - [ ] 驳回扣信用分（app_config: credit.penalty.reject，默认 5）
- [ ] **任务收尾判定**：最后一个进行中 claim 审完 → task 状态推进
  - [ ] 全部 APPROVED → task REVIEWING →（§4.1 结算）SETTLED
  - [ ] 有 REJECTED/CANCELLED → 语义确认（是否影响 SETTLED，见待拍板）
- [ ] 审核超时自动通过（与 §2.4 定时任务联动）

### 2.4 定时任务（schedule）✅

- [ ] `@EnableScheduling` 任务类 + **幂等/防重入**（单实例 @Scheduled；多实例 Redisson）
- [ ] 任务过期扫描（每分钟）：claim_deadline 过仍 OPEN→EXPIRED；deadline 过 IN_PROGRESS→EXPIRED
- [ ] 接取超时扫描（每分钟）：接取后 N 小时未提交 → claim CANCELLED + 扣信用分（app_config: claim.timeout.hours=72）
- [ ] 审核超时扫描（每分钟）：SUBMITTED 后 48h 未审 → 自动 APPROVED（app_config: review.timeout.hours + auto.approve.enabled）+ 通知
- [ ] 内容安全回查（每 5 分钟）：mediaCheckAsync 异步结果回填
- [ ] 对账扫描（每小时）：claimed_count 与 claim 行数不一致、状态异常 → audit_log 告警记录
- [ ] 时间统一用数据库 NOW()；失败批次可重跑

---

## 🎯 P3：文件上传 + 互评 + 举报（安全闭环）

### 3.1 文件模块（file）✅

- [ ] 实体/Mapper：`file` 表（storage_key UNIQUE）
- [ ] `POST /api/files` — 上传（multipart）
  - [ ] 类型白名单：jpg/png/webp（app_config: file.allowed.ext）
  - [ ] **magic bytes 校验**（不只信扩展名，读文件头）
  - [ ] 大小 ≤5MB（app_config: file.max.size.mb）
  - [ ] 服务端随机文件名 `biz/{type}/{yyyyMM}/{uuid}.ext`，禁用户原始文件名
  - [ ] 写 file 表（size/mime/uploader_id）+ audit_log
  - [ ] 上传限流（app_config: upload.rate.limit.per.minute）
- [ ] 图片内容安全：mediaCheckAsync 异步检测，未通过不可用于结算（状态位：pending/approved/rejected）
- [ ] 访问 URL 与存储抽象：本地存储（MVP）→ StorageService 接口，后续切 COS/OSS
- [ ] 后续：`POST /api/files/policy`（STS 直传，预留不实现）

### 3.2 互评模块（review）✅

- [ ] 实体/Mapper：`review` 表（UNIQUE(claim_id,from,to)）
- [ ] `POST /api/reviews` — 提交互评
  - [ ] score 1-5 校验、content ≤500、归属校验（claim 双方）
  - [ ] 唯一约束防重复互评（DuplicateKey → 409）
  - [ ] 内容安全 + audit_log

### 3.3 举报模块（report）✅

- [ ] 实体/Mapper：`report` 表（target_type/target_id/reason/status/handler_id）
- [ ] `POST /api/reports` — 提交举报
  - [ ] targetType 枚举校验：task/claim/review/user
  - [ ] reason ≤500、audit_log

---

## 🎯 P4：管理端 + 结算状态位（全量可演示）

### 4.1 管理端（admin，role=ADMIN 独立鉴权 @RequireAdmin）✅

- [ ] `GET /api/admin/tasks` — 全量任务（含删除标记）
- [ ] `PUT /api/admin/tasks/{id}/offline` — 下架违规任务（→CANCELLED + 通知发布者）
- [ ] `GET /api/admin/reports` — 举报列表（status 筛选）
- [ ] `PUT /api/admin/reports/{id}` — 处理举报（status 1=已处理/2=驳回 + note）
- [ ] `GET /api/admin/users` — 用户列表（status 筛选）
- [ ] `PUT /api/admin/users/{id}/ban` — 封禁（status 0→1，**jti 黑名单踢人下线**）
- [ ] `PUT /api/admin/users/{id}/unban` — 解封
- [ ] `GET /api/admin/stats/summary` — 统计（任务/用户/成交/待处理举报）
- [ ] 管理操作全部写 audit_log（handler_id）

### 4.2 结算状态位（settlement）✅

- [ ] 实体/Mapper：`settlement` 表（claim_id UNIQUE）
- [ ] task → SETTLED 时：对全部 APPROVED 的 claim 生成 settlement（amount=claim.reward 快照，status=待结算）
- [ ] MVP 只跑状态位，不打款（后续接支付另审）

---

## 🎯 P5：小程序端对接（上线准备，单独阶段）

- [x] 微信小程序工程（wxml/wxss/js）
- [x] wx.login → code2session → 登录态对接
- [x] 页面：任务列表/详情/发布/我的/接取/提交/审核/通知/**举报/互评/管理端**（11 页，本次补齐 4 页）
- [ ] 真机联调 + 域名备案 + HTTPS
- [ ] 类目资质确认（**上架硬门槛，最先确认**）

---

## 📋 横切任务（贯穿各阶段）

- [ ] 限流：Redis 令牌桶/滑动窗口，登录/接取/提交/上传分级（app_config 阈值）
- [ ] 缓存：任务列表/详情 Redis 缓存（先删缓存再写库 / 延迟双删）
- [ ] 结构化日志 + traceId（MDC 贯穿请求）
- [ ] /actuator/health 健康检查
- [ ] 单元测试：防超卖并发、状态机非法流转、IDOR 越权（至少核心 3 类）
- [ ] 生产配置：JWT secret / appsecret 环境变量 + 密钥零入 git（已部分完成）

---

## 待拍板（开发对应模块前需确认）

| # | 问题 | 建议 |
|---|---|---|
| 1 | 驳回凭证是否扣信用分、扣多少 | 扣 5（app_config 可配）|
| 2 | 任务有 REJECTED 时 SETTLED 语义 | 仅 APPROVED 结算，REJECTED 不结算 |
| 3 | 通知是否接微信订阅消息 | MVP 仅站内通知 |
| 4 | 图片存储本地路径 vs MinIO | 本地（零成本起步）|

---

## 完成标准（Go-Live 前提）

- [ ] P2-P4 全部接口通过端到端自测（沿用 P0/P1 的 curl 验证法）
- [ ] SECURITY_REVIEW.md §6 Go-Live Checklist 逐项打勾
- [ ] 并发压测无超卖（核验原子 SQL + 唯一索引）
- [ ] 越权用例全绿（审核/提交/取消/查看凭证/管理端）
- [ ] 定时任务幂等可重跑