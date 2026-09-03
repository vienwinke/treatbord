# Treatbord 数据库设计文档

> 依据方案大纲 `AGENTS.md` 与上架安全审核 `docs/SECURITY_REVIEW.md` 整理。
> 状态：设计稿 v0.1（已定稿关键取舍，DDL 见 `database/schema.sql`）

---

## 1. 总览与约定

| 项 | 决定 |
|---|---|
| 数据库实例 | `treatbord`，单库（dev/test/prod 同 schema，连接配置分离） |
| 字符集 / 排序 | `utf8mb4` / `utf8mb4_0900_ai_ci`（MySQL 8） |
| 时间字段 | **`DATETIME` 存 Asia/Shanghai 本地时间**（已拍板） |
| 状态字段 | **字符串枚举**（如 `OPEN`/`IN_PROGRESS`），可读性强、状态机 map 校验直观（已拍板） |
| 物理外键 | **不加**（已拍板。靠应用层校验 + 索引保证，MyBatis-Plus 与横向扩展友好） |
| 金额 | `DECIMAL(10,2)`，最大 99999999.99 |
| 逻辑删除 | 所有业务表含 `deleted TINYINT(1) NOT NULL DEFAULT 0`（0=正常 1=删除），查询默认过滤 |
| 通用审计列 | 所有业务表含 `id BIGINT PK AUTO_INCREMENT`、`create_time DATETIME`、`update_time DATETIME` |
| 注销策略 | **方案 A**（已拍板）：注销时对 `openid` 匿名化改写，释放唯一索引；重注册生成新 `user_id` |
| 应用账号 | `app_user`：仅业务库 DML（SELECT/INSERT/UPDATE/DELETE），无 DDL；DDL 由管理员账号执行 |
| JSON | 使用 MySQL 8 原生 `JSON` 类型 |

---

## 2. DDL 文件与执行说明

| 文件 | 用途 | 执行身份 |
|---|---|---|
| `database/schema.sql` | 建库、建表、建索引、建账号并授权 | 管理员（root 级） |
| `database/seed.sql` | 初始化基础数据（admin 账号、app_config 配置项等） | 管理员或 app_user |

> schema.sql 内含"重建"操作（`DROP DATABASE IF EXISTS`），**仅限开发/测试环境**执行；生产环境应使用增量迁移脚本。

---

## 3. 表结构设计

> 所有表均含 `id BIGINT AUTO_INCREMENT PRIMARY KEY`、`create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP`、`update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`、`deleted TINYINT(1) NOT NULL DEFAULT 0`。下文仅列业务字段。

### 3.1 user — 用户

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| openid | VARCHAR(64) | **UNIQUE 唯一**，按小程序隔离；注销时匿名化改写为 `DEL_<uuid>` |
| unionid | VARCHAR(64) | 可空，预留 |
| nickname | VARCHAR(30) | 昵称，≤30；需内容安全检测 |
| avatar | VARCHAR(255) | 头像 URL |
| credit_score | INT | 默认 100，信用分 |
| role | TINYINT(1) | 0=USER / 1=ADMIN |
| status | TINYINT(1) | 0=正常 / 1=封禁 |
| register_time | DATETIME | 注册时间（区别于 create_time，语义更明确） |

索引：`UNIQUE uk_openid(openid)`（注销匿名化后不冲突）

---

### 3.2 task — 任务

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| publisher_id | BIGINT | 发布者 user_id，索引 |
| title | VARCHAR(50) | 标题 ≤50，内容安全 |
| description | VARCHAR(2000) | 描述 ≤2000，内容安全 |
| reward | DECIMAL(10,2) | 报酬，非负 ≤2 位小数 |
| quota | INT | 名额数 |
| claimed_count | INT | 已接取数（原子扣减） |
| claim_deadline | DATETIME | 接取截止 |
| deadline | DATETIME | 完成截止 |
| status | VARCHAR(20) | 状态机：OPEN/IN_PROGRESS/REVIEWING/SETTLED/EXPIRED/CANCELLED |
| version | INT | 乐观锁 |

索引：`idx_task_status_claimdead(status, claim_deadline)`、`idx_task_status_ctime(status, create_time)`、`idx_task_publisher(publisher_id)`

原子扣减 SQL：
```sql
UPDATE task SET claimed_count = claimed_count + 1,
                version = version + 1
WHERE id = ? AND claimed_count < quota AND status = 'OPEN'
```

---

### 3.3 task_claim — 接取记录

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| task_id | BIGINT | 任务 id |
| user_id | BIGINT | 接取者 id |
| status | VARCHAR(20) | CLAIMED/SUBMITTED/APPROVED/REJECTED/CANCELLED |
| reward | DECIMAL(10,2) | **结算快照**（接取时固化，防改价） |
| submitted_at | DATETIME | 提交时间 |
| reviewed_at | DATETIME | 审核时间 |
| review_deadline | DATETIME | 审核截止（提交后 48h） |
| review_note | VARCHAR(500) | 审核备注 |

索引：`UNIQUE uk_claim_task_user(task_id, user_id)`（防重复接取）、`idx_claim_user_ctime(user_id, create_time)`、`idx_claim_status_revdl(status, review_deadline)`

---

### 3.4 task_submission — 提交凭证

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| claim_id | BIGINT | 接取 id，索引 |
| content | VARCHAR(2000) | 文字凭证 ≤2000，内容安全 |
| file_ids | JSON | 凭证图片 file.id 数组 |
| submit_time | DATETIME | 提交时间 |

索引：`idx_submission_claim(claim_id)`

说明：审核前可覆盖提交（MVP 保留最新一条有效提交）。

---

### 3.5 file — 文件

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| storage_key | VARCHAR(255) | **UNIQUE**，格式 `biz/{type}/{yyyyMM}/{uuid}.ext`，禁用户文件名 |
| url | VARCHAR(255) | 访问 URL |
| size | BIGINT | 字节数 |
| mime | VARCHAR(100) | MIME 类型 |
| uploader_id | BIGINT | 上传者 user_id |

索引：`UNIQUE uk_file_key(storage_key)`

---

### 3.6 notification — 通知

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| user_id | BIGINT | 接收者 |
| type | VARCHAR(30) | 通知类型 |
| title | VARCHAR(100) | 标题 |
| content | VARCHAR(500) | 内容 |
| biz_id | BIGINT | 关联业务 id |
| is_read | TINYINT(1) | 0=未读 / 1=已读 |

索引：`idx_noti_user_read_ctime(user_id, is_read, create_time)`

---

### 3.7 review — 互评

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| claim_id | BIGINT | 接取 id |
| from_user_id | BIGINT | 评价人 |
| to_user_id | BIGINT | 被评价人 |
| score | TINYINT | 1-5 |
| content | VARCHAR(500) | 内容，内容安全 |

约束：`UNIQUE uk_review(claim_id, from_user_id, to_user_id)`（防重复互评）

---

### 3.8 report — 举报

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| reporter_id | BIGINT | 举报人 |
| target_type | VARCHAR(30) | 被举报对象类型（task/claim/review/user） |
| target_id | BIGINT | 被举报对象 id |
| reason | VARCHAR(500) | 原因 |
| status | TINYINT(1) | 0=待处理 / 1=已处理 / 2=驳回 |
| handler_id | BIGINT | 处理人，可空 |

索引：`idx_report_status(status)`

---

### 3.9 settlement — 结算（预留）

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| claim_id | BIGINT | **UNIQUE** 接取 id |
| task_id | BIGINT | 任务 id |
| user_id | BIGINT | 结算对象 |
| amount | DECIMAL(10,2) | 金额 |
| status | TINYINT(1) | 0=待结算 / 1=已结算 |
| settle_time | DATETIME | 结算时间 |

约束：`UNIQUE uk_settle_claim(claim_id)`

说明：MVP 只跑状态位，不打款。

---

### 3.10 task_status_log — 任务状态审计

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| task_id | BIGINT | 任务 id |
| from_status | VARCHAR(20) | 原状态 |
| to_status | VARCHAR(20) | 新状态 |
| operator_id | BIGINT | 操作人 |
| reason | VARCHAR(500) | 原因 |

索引：`idx_tasklog_task(task_id)`

---

### 3.11 claim_status_log — 接取状态审计

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| claim_id | BIGINT | 接取 id |
| from_status | VARCHAR(20) | 原状态 |
| to_status | VARCHAR(20) | 新状态 |
| operator_id | BIGINT | 操作人 |
| reason | VARCHAR(500) | 原因 |

索引：`idx_claimlog_claim(claim_id)`

---

### 3.12 audit_log — 关键操作审计

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| user_id | BIGINT | 操作人，可空（系统操作） |
| action | VARCHAR(50) | 操作类型：login/claim/review/settle/ban... |
| target_type | VARCHAR(30) | 目标类型 |
| target_id | BIGINT | 目标 id |
| detail | VARCHAR(500) | 详情 |
| ip | VARCHAR(45) | 来源 IP |

索引：`idx_audit_user(user_id)`、`idx_audit_action_ctime(action, create_time)`

---

### 3.13 login_log — 登录记录

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| user_id | BIGINT | 用户 id |
| ip | VARCHAR(45) | IP |
| user_agent | VARCHAR(255) | UA |
| success | TINYINT(1) | 0=失败 / 1=成功 |
| fail_reason | VARCHAR(100) | 失败原因 |

索引：`idx_login_user(user_id)`、`idx_login_ctime(create_time)`

---

### 3.14 app_config — 配置

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| config_key | VARCHAR(50) | **UNIQUE** 配置键 |
| config_value | VARCHAR(500) | 配置值 |

约束：`UNIQUE uk_config_key(config_key)` — 该表**不含 deleted**（配置不逻辑删）

说明：限流阈值、审核窗口、文件上限等可运行期调整，不重启发版。

---

## 4. 关键设计决策记录

1. **时间统一 DATETIME + Asia/Shanghai**：直观可读，避免时区换算歧义；应用侧一律用 `Asia/Shanghai` JDBC 时区。
2. **状态字符串枚举**：状态机流转用"枚举 + 迁移 map"校验，非法迁移拒绝；DDL 不设 CHECK 约束（迁移灵活性），由应用层保证。
3. **无物理外键**：靠唯一索引 / 普通索引 + 应用层归属校验，减少锁竞争、便于拆库。
4. **注销（方案 A）**：注销时 `openid` 改写为 `DEL_<uuid>`（匿名化 + 释放唯一索引），历史数据保留、审计可追溯且不泄露原始 openid；重注册生成新 `user_id`。
5. **reward 结算快照**：`task_claim.reward` 在接取时固化，审核结算一律以快照为准，防发布者改价影响已接取者。
6. **原子扣减 + 乐观锁 + 唯一索引**：三级并发防线防超卖（见 §3.2 SQL）。
7. **逻辑删除 + 唯一索引冲突**：仅 `user.openid` 存在此问题，用方案 A 匿名化解决；其余唯一索引（claim/review/settlement）不会因逻辑删除重复，故保持单纯唯一索引。

---

## 5. 待办 / 后续

- [ ] 生产环境增量迁移脚本规范（Flyway/Liquibase，替代 DROP 重建）
- [ ] 内容安全检测状态位（task/claim 若需"待检测"中间态，在提交/发布接口层实现）
- [ ] admin 初始化账号密码需从环境变量注入（不入库明文）
