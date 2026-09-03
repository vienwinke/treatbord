# Treatbord 功能接口设计（API 规格 v0.1）

> 依据 `AGENTS.md`（§7 接口设计）与 `docs/DB_DESIGN.md`（表结构）细化。
> 状态：**评审稿**——审核通过后即可照此实现。所有接口遵循统一约定（见 §1）。

---

## 1. 全局约定

### 1.1 基础信息

| 项 | 约定 |
|---|---|
| Base URL | `https://<api-domain>/api`（本地开发 `http://localhost:8080/api`）|
| 数据格式 | 全 JSON（`application/json;charset=UTF-8`）|
| 认证方式 | `Authorization: Bearer <JWT>`（登录后获得）|
| 分页 | `page`（从 1 起）、`pageSize`（默认 10，**上限 20**）|
| 时间格式 | `yyyy-MM-dd HH:mm:ss`（Asia/Shanghai，与库内 DATETIME 一致）|
| 金额 | `DECIMAL(10,2)`，JSON 数字 |

### 1.2 统一响应体 `Result<T>`

```json
{
  "code": 0,           // 0=成功；非 0=业务错误码
  "message": "ok",     // 错误时的可读信息
  "data": { ... },     // 业务数据（可为 null）
  "traceId": "a1b2..." // 全链路追踪，排障用
}
```

**通用错误码**（业务错误码从 1000 起，见各节）：

| code | 含义 |
|---|---|
| 0 | 成功 |
| 400 | 参数错误（`spring-boot-starter-validation` 校验失败）|
| 401 | 未登录 / token 失效 |
| 403 | 无权限（资源不属于当前用户 / 非 ADMIN）|
| 404 | 资源不存在 |
| 409 | 状态冲突（非法状态流转）|
| 429 | 触发限流 |
| 500 | 服务器内部错误 |
| 1001 | openid 解析失败（code2session 异常）|
| 1002 | 用户已封禁 |
| ... | 其余见各接口 |

### 1.3 分页响应

```json
{
  "code": 0,
  "data": {
    "list": [ ... ],
    "total": 100,
    "page": 1,
    "pageSize": 10
  }
}
```

### 1.4 安全与审计约定

- 所有「按 id 操作他人资源」的接口：**服务端强制校验资源归属**，返回 403。
- 所有状态变更：写入 `task_status_log` / `claim_status_log`。
- 登录、接取、提交、审核、结算、封禁：写入 `audit_log`。
- 状态机迁移：仅合法流转，非法返回 409。
- 内容安全：发布/提交/互评等 UGC 入库前过 `msgSecCheck`（未定稿按开关可关）。

---

## 2. 认证模块

### 2.1 用户登录（微信小程序 code2session）

| 项 | 值 |
|---|---|
| 接口 | `POST /api/auth/login` |
| 鉴权 | 无（匿名） |
| 限流 | 30/分/IP（app_config: login.rate.limit.per.minute）|

**请求**
```json
{ "code": "wx.login 拿到的临时 code" }
```

**流程**：code → code2session → 换 openid → 查/建 `user` → 签发 JWT（HS256，含 `userId`、`jti`、`role`、`status`，7 天）→ 写 `login_log` + `audit_log`。

**响应**
```json
{
  "code": 0,
  "data": {
    "token": "<JWT>",
    "expiresIn": 604800,
    "user": { "id": 1, "nickname": "小明", "avatar": "https://...", "creditScore": 100, "role": 0 }
  }
}
```

**错误**：`1001` openid 解析失败；`1002` 用户已封禁（登录拒绝）。

### 2.2 退出登录

| 项 | 值 |
|---|---|
| 接口 | `POST /api/auth/logout` |
| 鉴权 | 登录 |

**说明**：将当前 JWT 的 `jti` 加入黑名单（Redis，TTL=剩余有效期），此后该 token 失效；写 `audit_log`。

### 2.3 刷新/获取当前用户信息

| 项 | 值 |
|---|---|
| 接口 | `GET /api/users/me` |
| 鉴权 | 登录 |

**响应**
```json
{ "code": 0, "data": { "id": 1, "nickname": "小明", "avatar": "...", "creditScore": 100, "role": 0, "status": 0, "registerTime": "2026-09-02 11:00:00" } }
```

### 2.4 注销账号（合规强制）

| 项 | 值 |
|---|---|
| 接口 | `DELETE /api/users/me` |
| 鉴权 | 登录 |

**说明**：软删 + **openid 匿名化**（改写为 `DEL_<uuid>`，方案 A）；关联个人数据匿名化或保留（按 §5 隐私策略）；JWT jti 入黑名单；写 `audit_log`。

---

## 3. 任务模块

### 3.1 任务列表（分页/筛选/搜索）

| 项 | 值 |
|---|---|
| 接口 | `GET /api/tasks` |
| 鉴权 | 登录（可选）|

**查询参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| page / pageSize | int | 分页（pageSize ≤ 20）|
| status | string | 可选：OPEN / IN_PROGRESS / REVIEWING / SETTLED / EXPIRED / CANCELLED |
| keyword | string | 标题/描述模糊搜索（≤50）|
| minReward / maxReward | number | 报酬区间 |
| publisherId | long | 指定发布者（管理用）|

**说明**：默认只列 `deleted=0`；列表查询时**惰性判断**：已过 `claim_deadline` 仍 OPEN 的任务 → 顺带触发 EXPIRED 兜底更新。

**响应**：分页；每项含 `id,title,reward,quota,claimedCount,claimDeadline,deadline,status,publisher{id,nickname},createTime`。

### 3.2 任务详情

| 项 | 值 |
|---|---|
| 接口 | `GET /api/tasks/{id}` |
| 鉴权 | 登录（可选）|

**响应**：任务全字段 + `description` + 发布者信息 + 当前用户是否已接取（登录态）。

### 3.3 发布任务

| 项 | 值 |
|---|---|
| 接口 | `POST /api/tasks` |
| 鉴权 | 登录 |

**请求**（`@Valid` 校验）
```json
{
  "title": "帮忙拍一张产品照",           // ≤50，非空
  "description": "需要手机拍摄...",       // ≤2000，可空
  "reward": 50.00,                      // ≥0，≤2 位小数
  "quota": 3,                           // ≥1，≤100
  "claimDeadline": "2026-09-05 18:00:00", // 接取截止
  "deadline": "2026-09-08 18:00:00"      // 完成截止（须晚于 claimDeadline）
}
```

**校验**：标题/描述过 `msgSecCheck`（违规拦截 400）；`claimDeadline < deadline`；quota ≤ 100。

**响应**：`data = { id }`（新任务，status=OPEN）。

### 3.4 任务状态流转接口（发布者取消任务）

| 项 | 值 |
|---|---|
| 接口 | `POST /api/tasks/{id}/cancel` |
| 鉴权 | 登录且为 `publisher_id` |

**说明**：仅 `OPEN / IN_PROGRESS` 可取消 → `CANCELLED`；写 `task_status_log`；若有已接取（IN_PROGRESS），同步取消名下未完成 claim（写 `claim_status_log`）。

---

## 4. 接取模块

### 4.1 接取任务

| 项 | 值 |
|---|---|
| 接口 | `POST /api/tasks/{id}/claim` |
| 鉴权 | 登录 |
| 限流 | 20/分/IP（claim.rate.limit.per.minute）|

**事务内顺序（核心，防超卖）**

1. **原子扣减**：`UPDATE task SET claimed_count=claimed_count+1, version=version+1 WHERE id=? AND status='OPEN' AND claimed_count<quota`，影响 0 行 → 409「已满/不可接取」；
2. 查 task 快照：`reward` 固化进 claim（结算快照）；
3. 校验 `user_id != task.publisher_id`（防自接自单）→ 违规 403；校验 `credit_score >= 阈值`（app_config）→ 不足 403；
4. 插入 `task_claim(status=CLAIMED, reward=快照, review_deadline=null, submitted_at=null)`；
5. task `OPEN → IN_PROGRESS`（CAS）；
6. 唯一索引 `(task_id,user_id)` 兜底：冲突 → **回滚**（原子扣减一并回滚，杜绝超卖）；写 `claim_status_log` + `audit_log`。

**响应**：`data = { claimId }`。

### 4.2 取消接取

| 项 | 值 |
|---|---|
| 接口 | `DELETE /api/claims/{id}` |
| 鉴权 | 登录且为 `user_id` |

**说明**：仅 `CLAIMED`（未提交）可取消，`claim_deadline` 已过则不可取消（走超时路径）→ `CANCELLED`，同时 **`claimed_count` 回减 1**（CAS：`claimed_count-1 WHERE id=? AND claimed_count>0`）；写 `claim_status_log`。任务若无其他进行中 claim，可回退 `IN_PROGRESS → OPEN`。

### 4.3 我接取的任务

| 项 | 值 |
|---|---|
| 接口 | `GET /api/me/claims` |
| 鉴权 | 登录 |

**响应**：分页；每项含 `claim{id,status,reward,createTime}` + `task{id,title,deadline,status}`。

---

## 5. 凭证模块

### 5.1 提交完成凭证

| 项 | 值 |
|---|---|
| 接口 | `POST /api/claims/{id}/submit` |
| 鉴权 | 登录且为 `user_id` |
| 限流 | 20/分/IP（submit.rate.limit.per.minute）|

**请求**
```json
{
  "content": "已完成，照片如下",     // ≤2000
  "fileIds": [3, 5]                // 已上传的凭证图 file.id
}
```

**说明**
- `CLAIMED → SUBMITTED`（CAS），更新 `submitted_at=NOW()`、`review_deadline=NOW()+48h`；
- 写 `task_submission`（**审核前可覆盖**：同一 claim 再次 submit 时更新/替换最新一条）；
- 内容与图片过内容安全（未过：拦截/标记待复核）；
- 写 `claim_status_log` + `audit_log`。

### 5.2 查看接取详情（含凭证）

| 项 | 值 |
|---|---|
| 接口 | `GET /api/claims/{id}` |
| 鉴权 | 登录且为 `user_id` **或** `task.publisher_id` |

**响应**：claim 全字段 + 最新 submission（content、fileIds→文件 URL 列表）+ review 结果（approve/reject、note）。

---

## 6. 审核模块

### 6.1 审核凭证

| 项 | 值 |
|---|---|
| 接口 | `POST /api/claims/{id}/review` |
| 鉴权 | 登录且为 `task.publisher_id` |

**请求**
```json
{ "action": "approve" | "reject", "note": "可选，≤200" }
```

**说明**
- `SUBMITTED → APPROVED / REJECTED`（CAS）；更新 `reviewed_at=NOW()`；
- 写 `review_note`、`claim_status_log`、`audit_log`；发 `notification` 通知接取者；
- **任务收尾判定**：若全部 claim 为 APPROVED/REJECTED/CANCELLED（无进行中）→ task 进入 `REVIEWING`（全部通过才 `SETTLED`，见 §9 结算）；
- 驳回：接取者信用分扣减（app_config 阈值，可选）。

### 6.2 我发布的任务

| 项 | 值 |
|---|---|
| 接口 | `GET /api/me/tasks` |
| 鉴权 | 登录（仅返回 `publisher_id=当前用户`）|

**响应**：分页；每项含任务概要 + **接取进度**（claimedCount/quota）+ 待审核 claim 数。

---

## 7. 文件模块

### 7.1 上传文件（MVP 直传）

| 项 | 值 |
|---|---|
| 接口 | `POST /api/files` |
| 鉴权 | 登录 |
| Content-Type | `multipart/form-data`，字段名 `file` |
| 限流 | 20/分/IP（upload.rate.limit.per.minute）|

**校验（P0 安全，见 SECURITY_REVIEW C1-C4）**

| 校验 | 规则 |
|---|---|
| 类型白名单 | jpg / png / webp（app_config: file.allowed.ext）|
| magic bytes | 校验文件头（不只信扩展名），不符拒绝 |
| 大小 | ≤ 5MB（app_config: file.max.size.mb）|
| 文件名 | 服务端生成 `biz/{type}/{yyyyMM}/{uuid}.ext`，**禁止用户原始文件名** |
| 内容安全 | 过 `mediaCheckAsync`（图片），未通过前标记待复核，不可用于结算 |

**响应**：`data = { id, url, size, mime, createTime }`。

> 后续演进：`POST /api/files/policy`（STS 临时凭证直传 COS/OSS，本版不做）。

---

## 8. 通知模块

### 8.1 通知列表

| 项 | 值 |
|---|---|
| 接口 | `GET /api/notifications` |
| 鉴权 | 登录 |

**参数**：`page/pageSize`、`isRead`（可选 0/1）。返回 `user_id=当前用户` 的分页列表。

### 8.2 标记已读

| 项 | 值 |
|---|---|
| 接口 | `POST /api/notifications/{id}/read` |
| 鉴权 | 登录且为 `user_id` |

**说明**：`is_read 0→1`（幂等：已读再调返回成功）；可批量：`POST /api/notifications/read-all`。

---

## 9. 互评与结算

### 9.1 提交互评

| 项 | 值 |
|---|---|
| 接口 | `POST /api/reviews` |
| 鉴权 | 登录（claim 双方，task 完成后）|

**请求**
```json
{ "claimId": 1, "toUserId": 6, "score": 5, "content": "靠谱，推荐（≤500）" }
```

**说明**：唯一约束 `(claim_id, from_user_id, to_user_id)` 防重复互评；score 1-5 校验；过内容安全。

### 9.2 结算（预留，MVP 只跑状态位）

| 项 | 值 |
|---|---|
| 接口 | `POST /api/tasks/{id}/settle`（内部/定时任务触发，非公开）|

**说明**：task `REVIEWING → SETTLED` 时，对全部 APPROVED 的 claim 写 `settlement(status=待结算, amount=claim.reward 快照, settle_time=null)`；打款后续接入，本版只落状态位。

---

## 10. 举报模块

### 10.1 提交举报

| 项 | 值 |
|---|---|
| 接口 | `POST /api/reports` |
| 鉴权 | 登录 |

**请求**
```json
{ "targetType": "task" | "claim" | "review" | "user", "targetId": 1, "reason": "违规内容...（≤500）" }
```

**说明**：入库 `status=待处理`；写 `audit_log`。

---

## 11. 管理端（`/api/admin/*`，独立鉴权 role=ADMIN）

| 接口 | 方法 | 说明 |
|---|---|---|
| `GET /api/admin/tasks` | GET | 分页查所有任务（含已删除标记）|
| `PUT /api/admin/tasks/{id}/offline` | PUT | 下架违规任务（→ CANCELLED，通知发布者）|
| `POST /api/admin/tasks/{id}/restore` | POST | 恢复（仅 EXPIRED/CANCELLED 可回 OPEN？**待语义裁定**）|
| `GET /api/admin/reports` | GET | 举报列表（按 status 筛选）|
| `PUT /api/admin/reports/{id}` | PUT | 处理举报：`{status: 1|2, note}`（已处理/驳回）|
| `GET /api/admin/users` | GET | 用户列表（含 status 筛选）|
| `PUT /api/admin/users/{id}/ban` | PUT | 封禁用户（`status 0→1`，jti 黑名单**踢人下线**）|
| `PUT /api/admin/users/{id}/unban` | PUT | 解封 |
| `GET /api/admin/stats/summary` | GET | 统计：任务数/用户数/成交数/举报待处理数等 |

> 管理端每个写操作均写 `audit_log`（handler_id=操作人）。

---

## 12. 定时任务（非接口，后台扫描）

| 任务 | 频率 | 处理逻辑（幂等 + 防重入）|
|---|---|---|
| 任务过期扫描 | 每分钟 | `claim_deadline` 已过仍 OPEN → EXPIRED（无接取）；`deadline` 已过 IN_PROGRESS → EXPIRED |
| 接取超时扫描 | 每分钟 | `review_deadline`… 接取后 72h 未提交（app_config: claim.timeout.hours）→ claim CANCELLED + 扣信用分 |
| 审核超时扫描 | 每分钟 | SUBMITTED 后 48h 未审核（app_config: review.timeout.hours + auto.approve.enabled）→ 自动 APPROVED + 通知 |
| 内容安全回查 | 每 5 分钟 | 异步检测结果回填（mediaCheckAsync 回调/轮询）|
| 对账扫描 | 每小时 | claimed_count 与 claim 行数不符、状态异常 → 告警（audit_log 记录）|

> 所有定时任务：单实例 `@Scheduled`；多实例 Redisson 分布式锁防重入；时间以数据库 `NOW()` 为准。

---

## 13. 接口总数与实现优先级

**合计公开接口：约 23 个**（认证 4 + 任务 4 + 接取 3 + 凭证 2 + 审核 2 + 文件 1 + 通知 3 + 互评 1 + 举报 1 + 管理 9，不含定时任务）。

**建议实现顺序（后端优先）**

| 阶段 | 范围 | 里程碑 |
|---|---|---|
| P0 | 项目骨架 + 登录 + Result/异常 + JWT | 能登录、能鉴权 |
| P1 | 任务发布/列表/详情 + 接取(防超卖)/取消 | 核心闭环可跑 |
| P2 | 凭证提交 + 审核 + 通知 + 定时任务 | 完整业务闭环 |
| P3 | 文件上传 + 内容安全 + 互评/举报 | 安全闭环 |
| P4 | 管理端 + 结算状态位 | 全量可演示 |
| P5 | 小程序端对接（后续）| 上线准备 |

---

## 14. 待拍板（接口层面）

| # | 问题 | 建议 |
|---|---|---|
| 1 | 接取时信用分门槛阈值多少 | app_config 可配，默认 60 |
| 2 | 管理员「恢复已取消任务」是否开放 | 保守：不开放（本版仅下架）|
| 3 | 驳回凭证是否扣信用分、扣多少 | 默认扣 5（app_config 可配）|
| 4 | 通用通知「read-all」是否需要 | 建议要，前端省逐条点 |