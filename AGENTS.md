# Treatbord — 任务接取平台

> 本文件是项目**方案大纲**（开发蓝图）。上架安全与可靠性审核详见 `docs/SECURITY_REVIEW.md`。

## 1. 项目概述

微信小程序「任务接取」平台：发布者发布任务 → 接取者接取并提交完成凭证 → 发布者审核 → 结算。

## 2. MVP 范围

**做**：用户登录、任务发布/列表/详情、接取/取消、提交凭证、发布者审核、互评、站内通知、举报、内容安全检测、管理端（内容处置/封禁）。

**不做**（预留不实现）：真实支付打款（保留 reward 与结算状态位）、短信验证、第三方登录、设备指纹风控、WebSocket 实时推送。

## 3. 技术栈（已确认）

- Java 21（LTS）+ Spring Boot 3.x + Maven
- MySQL 8（utf8mb4 / Asia/Shanghai）+ Redis（AOF 持久化）
- MyBatis-Plus ORM（分页插件，禁止 `${}` 拼接）
- 微信登录：wx.login → code2session → 签发 JWT
- 补充：spring-boot-starter-validation、springdoc-openapi、Redisson（备而不用）、Spring @Scheduled（定时任务）、logback 结构化日志

## 4. 核心领域模型

用户、任务（task）、接取（claim）、提交凭证（submission）、文件（file）、通知（notification）、互评（review）、举报（report）、结算（settlement，预留）、审计日志（status_log / audit_log）。

## 5. 数据表设计（10 张）

> 约定：所有表含 `id BIGINT 主键`、`create_time`、`update_time`、`deleted`（逻辑删除）。

| 表 | 关键字段 | 说明 / 约束 |
|---|---|---|
| **user** | openid(UNIQUE)、unionid(可空,预留)、nickname、avatar、credit_score(默认100)、role(0=USER/1=ADMIN)、status(0=正常/1=封禁) | openid 按小程序隔离；封禁需踢人下线（JWT jti） |
| **task** | publisher_id(索引)、title(≤50)、description(≤2000)、reward DECIMAL(10,2)、quota、claimed_count、claim_deadline、deadline、status、version | **原子扣减**：`UPDATE task SET claimed_count=claimed_count+1 WHERE id=? AND claimed_count<quota AND status='OPEN'`；version 乐观锁；索引 (status,claim_deadline)、(status,create_time) |
| **task_claim** | task_id、user_id、status、reward(**结算快照**)、submitted_at、reviewed_at、review_deadline、review_note | 唯一索引 **(task_id,user_id)** 防重复接取；索引 (user_id,create_time)、(status,review_deadline) |
| **task_submission** | claim_id(索引)、content(≤2000)、file_ids JSON、submit_time | 审核前可覆盖提交（MVP 保留最新） |
| **file** | storage_key(UNIQUE)、url、size、mime、uploader_id | 凭证图/头像共用；key 格式 `biz/{type}/{yyyyMM}/{uuid}.ext`，禁用户文件名 |
| **notification** | user_id、type、title、content、biz_id、is_read | 索引 (user_id,is_read,create_time)；站内为主 |
| **review** | claim_id、from_user_id、to_user_id、score(1-5)、content | 唯一约束 (claim_id,from_user_id,to_user_id) 防重复互评 |
| **report** | reporter_id、target_type、target_id、reason、status、handler_id | 举报闭环：待处理→已处理/驳回；内容安全配套 |
| **settlement** | claim_id(UNIQUE)、task_id、user_id、amount DECIMAL(10,2)、status、settle_time | 结算预留：MVP 只跑状态位，不打款 |
| **task_status_log / claim_status_log** | task_id/claim_id、from_status、to_status、operator_id、reason | **状态变更审计**，排查并发问题的依据，必做 |
| **audit_log / login_log** | 关键操作（登录/接取/审核/结算/封禁）+ 登录记录 | 安全审计与后续风控基础 |
| **app_config** | config_key(UNIQUE)、config_value | 限流阈值、审核窗口、文件上限等可运行期调整 |

## 6. 状态机

**task**：`OPEN →(有人接取)→ IN_PROGRESS →(提交齐/到截止)→ REVIEWING →(全部审核通过)→ SETTLED`
　`OPEN →(接取截止无人/到期未完成)→ EXPIRED`；`OPEN/IN_PROGRESS →(发布者取消)→ CANCELLED`

**claim**：`CLAIMED →(提交)→ SUBMITTED →(通过)→ APPROVED`、`→(拒绝)→ REJECTED`
　`CLAIMED →(主动取消/超时未提交)→ CANCELLED`；`SUBMITTED →(审核超时自动)→ APPROVED`

实现：枚举 + 迁移 map 校验非法流转；所有迁移写 status_log；更新全部走 CAS 式（`SET status=? WHERE id=? AND status=旧状态`）防竞态。

## 7. 接口设计

| 模块 | 接口 |
|---|---|
| 认证 | `POST /api/auth/login`、`POST /api/auth/logout`、`DELETE /api/users/me`（注销，合规强制） |
| 任务 | `GET /api/tasks`（分页/筛选/搜索）、`GET /api/tasks/{id}`、`POST /api/tasks`（发布） |
| 接取 | `POST /api/tasks/{id}/claim`、`DELETE /api/claims/{id}`（截止前取消）、`GET /api/me/claims` |
| 凭证 | `POST /api/claims/{id}/submit`、`GET /api/claims/{id}` |
| 审核 | `POST /api/claims/{id}/review`（approve/reject）、`GET /api/me/tasks`（我发布的） |
| 文件 | `POST /api/files`（MVP）→ 后续 `POST /api/files/policy`（STS 直传） |
| 通知 | `GET /api/notifications`、`POST /api/notifications/{id}/read` |
| 互评 | `POST /api/reviews` |
| 举报 | `POST /api/reports` |
| 管理 | `/api/admin/*`（内容处置、封禁、统计，独立鉴权 role=ADMIN） |

统一：响应体 `Result<T>`、统一异常处理、分页参数 `page/pageSize`（上限 20）。

## 8. 关键设计决策（推荐方案，默认采用）

1. **超时与取消**：双截止（claim_deadline/deadline）；接取者超时未提交 → 自动取消+扣信用分；任务到期未完成 → EXPIRED 释放名额；定时任务扫描 + 查询时惰性判断兜底
2. **并发防超卖**：原子 SQL 扣减（见 task 表）+ 唯一索引 + version 乐观锁；Redis 锁仅用于防重入
3. **防作弊**：唯一索引防重复接取；接取时校验 `user_id != task.publisher_id` 防自接自单；信用分门槛；小号/设备指纹后置
4. **审核兜底**：提交后 48h 未审核自动通过 + 申诉窗口（**待拍板**，见 §10）
5. **凭证上传**：StorageService 抽象接口，MVP 本地/MinIO，后续切 COS/OSS；白名单 + magic bytes + 大小限制 + 内容安全检测
6. **状态机**：枚举 + 迁移 map + status_log 审计（见 §6）
7. **权限**：按资源归属校验（`task.publisher_id`/`claim.user_id`），抽公共校验，禁止散落判断；admin 独立前缀
8. **登录安全**：code2session + JWT(HS256、≥32字节密钥、7 天、载荷含 jti)；session_key 只存服务端不下发
9. **通知**：站内通知表为主，微信订阅消息仅做关键节点（审核结果）提醒（订阅消息一次性授权，当不了主渠道）
10. **结算预留**：claim.reward 结算快照（防改价）+ settlement 表；MVP 只跑状态位

## 9. 上架合规与安全底线（P0 摘要）

详见 `docs/SECURITY_REVIEW.md`。开发前必须定稿的底线：

- **类目资质**：任务/兼职类小程序可能需人力资源类资质，需提前确认类目（待办）
- **隐私合规**：用户协议 + 隐私政策 + 注销入口 + 微信隐私保护指引
- **内容安全**：msgSecCheck（文本）+ mediaCheckAsync（图片），含拦截/下架/申诉闭环
- **越权防护**：所有按 id 接口校验资源归属
- **密钥管理**：JWT secret / appsecret / 存储密钥全部环境变量，禁止入 git
- **文件上传**：类型白名单 + magic bytes + 大小限制 + 随机文件名
- **生产环境**：HTTPS + 域名备案、DB 内网可达 + 最小权限 + 每日备份、日志脱敏

## 10. 待拍板问题（4 条）

| # | 问题 | 推荐选项 | 影响 |
|---|---|---|---|
| 1 | 审核兜底策略 | **超时自动通过 + 申诉窗口**（对 C 端友好） | 状态机 + 定时任务设计 |
| 2 | 对象存储方案 | **本地/MinIO**（可自托管、零成本起步） | 文件模块实现 |
| 3 | 是否收集手机号 | **不收集**（MVP 最小化收集） | 隐私合规范围 |
| 4 | 小程序类目/资质 | 需确认类目归属及所需资质 | 上架硬门槛，最先确认 |

## 11. 开发环境

- 已安装：OpenJDK 21.0.12（LTS）+ Maven 3.9.12，运行于 WSL2 (Ubuntu)
- 开发顺序：后端优先（接口 + 表结构 → 管理端 → 小程序端）
