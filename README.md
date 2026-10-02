<div align="center">

# Treatbord · 任务接取平台

**微信小程序 + Spring Boot 前后端分离的任务撮合系统**

发布者发布任务 → 接取者接取并提交凭证 → 发布者审核 → 结算

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.4-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-8-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.12-blue)](https://baomidou.com/)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](#-license)

</div>

---

## 📖 项目简介

Treatbord 是一个面向微信小程序的任务接取平台。用户可以发布悬赏任务（如拍照、取快递、翻译资料），其他用户接取并在完成后提交凭证，发布者审核通过后进入结算流程。

项目采用**前后端分离**架构：后端提供 RESTful API（34 个接口），前端为微信小程序原生开发（17 个页面），并按上架标准实现了**越权防护、内容安全检测、隐私合规**等要求。

**规模**：后端 116 个 Java 文件 / 7.1k 行 · 34 个接口 · 14 张表 · 7 份产品文档 · 小程序 17 个页面 · 93 个自动化测试用例

---

## ✨ 核心功能

### 用户端（小程序）

| 模块 | 功能 |
|---|---|
| 认证 | 微信一键登录、账号密码登录、设置/修改账密、注销账号（数据匿名化）|
| 任务 | 任务大厅（分页/状态筛选/关键词搜索）、发布任务、任务详情、取消任务 |
| 接取 | 接取任务、取消接取、我的接取、我的发布 |
| 凭证 | 提交完成凭证（文字 + 图片上传）、审核前可覆盖提交 |
| 审核 | 发布者审核凭证（通过/驳回 + 备注）|
| 互动 | 互评（1-5 星）、举报、站内通知（已读/全部已读）|
| 合规 | 用户协议、隐私政策 |
| AI 问答 | 自然语言问数据（流式回答 + 表格 + 图表 + 引用）；问句不含 user_id，身份在 Java 侧完成 |

### 管理端

任务下架 · 用户封禁（**踢下线**）/解封 · 举报处理 · 平台统计

---

## 🏗️ 系统架构

```
┌──────────────────────────────────────────────────────────────┐
│                    微信小程序（17 页面）                        │
│   任务大厅 · 发布 · 详情 · 我的 · 提交凭证 · 审核 · 通知 · 管理   │
└───────────────────────────┬──────────────────────────────────┘
                            │ HTTPS + JWT (Bearer)
┌───────────────────────────▼──────────────────────────────────┐
│                      Spring Boot 应用                          │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ 拦截器链：限流（Redis 固定窗口） → 鉴权（JWT + 黑名单）      │  │
│  ├────────────────────────────────────────────────────────┤  │
│  │ Controller  →  Service（事务边界/状态机）  →  Mapper      │  │
│  │      ↓              ↓                        ↓         │  │
│  │   DTO/VO      业务规则 + CAS 流转          原子 SQL       │  │
│  └────────────────────────────────────────────────────────┘  │
│  横切：traceId 链路追踪 · 审计日志 · 内容安全 · 定时任务          │
└──────┬─────────────────────┬──────────────────┬───────────────┘
       │                     │                  │
┌──────▼──────┐   ┌──────────▼──────┐   ┌───────▼────────┐
│  MySQL 8.4  │   │     Redis       │   │  微信开放接口    │
│  14 张表     │   │ 黑名单/限流/缓存  │   │ 登录·内容安全    │
│  Flyway 迁移 │   │ access_token    │   │                │
└─────────────┘   └─────────────────┘   └────────────────┘
                                    ┌────────────────────┐
                                    │ 对象存储（本地/OSS） │
                                    └────────────────────┘
```

**AI 问答链路（跨进程）**

```
小程序 pages/ai-chat
  │ SSE（wx.connectSocket，或 chunked 分片自行切分）
  ▼
module/ai 接入层（Java）：内部 JWT + 逐帧转发
  │ SSE
  ▼
agent 边车（Python · FastAPI · /v1/ai/*）—— 独立进程/独立仓库
  │ 只读 SQL（三层护栏：sqlglot 静态校验 → 只读沙箱 → EXPLAIN 限额）
  ▼
MySQL 14 张业务表（最小权限只读账号）
```

**分层说明**

| 层 | 职责 |
|---|---|
| 拦截器层 | 限流 → 鉴权（JWT 校验 + jti 黑名单 + 封禁拦截 + `@RequireAdmin`）|
| Controller | 参数校验（Bean Validation）、DTO/VO 转换，**不返回实体** |
| Service | 业务规则、状态机流转、事务边界、审计埋点 |
| Mapper | MyBatis-Plus + 注解 SQL（原子扣减/CAS 更新）|

---

## 🔥 技术亮点

### 1. 并发防超卖：三级防线

**问题**：任务名额有限（quota），并发接取时可能出现超卖。

**方案**：
```java
// 第一道：原子 SQL 扣减（数据库行锁保证）
UPDATE task SET claimed_count = claimed_count + 1
WHERE id = ? AND claimed_count < quota AND status IN ('OPEN','IN_PROGRESS')

// 第二道：唯一索引兜底（同一用户重复接取）
UNIQUE KEY uk_claim_task_user (task_id, user_id)

// 第三道：CAS 状态流转（防竞态）
UPDATE task SET status = ? WHERE id = ? AND status = 旧状态
```
**验证**：并发测试中 3 个用户同时抢 2 个剩余名额 → 恰好 2 成功 1 失败，`claimed_count` 与 `task_claim` 实际行数一致，**零超卖**。

### 2. 状态机驱动：非法流转零容忍

任务（`OPEN → IN_PROGRESS → REVIEWING → SETTLED`）与接取（`CLAIMED → SUBMITTED → APPROVED/REJECTED`）均以**枚举 + 迁移表**约束：

```java
private static final Map<TaskStatus, Set<TaskStatus>> TRANSITIONS = Map.of(
    OPEN, Set.of(IN_PROGRESS, EXPIRED, CANCELLED),
    IN_PROGRESS, Set.of(REVIEWING, EXPIRED, CANCELLED),
    ...
);
// 非法流转直接抛 409，且所有迁移写入 status_log 审计表
```

### 3. 认证与越权防护

- **JWT（HS256，含 `jti`）**：登出/封禁时将 `jti` 写入 Redis 黑名单 → **立即踢人下线**
- **资源归属校验**：所有按 id 操作的接口强制校验 `task.publisher_id` / `claim.user_id`（防 IDOR），实测越权访问返回 403
- **多级限流**：Redis 固定窗口，登录/接取/提交/上传分级阈值（存 `app_config` 可热调）
- **密码安全**：BCrypt（预哈希 + pepper），兼容历史 SHA-256 哈希并**登录时自动升级**

### 4. 内容安全闭环（上架强制项）

```
用户输入 → msgSecCheck（文本，同步拦截 risky） 
        → mediaCheckAsync（图片，异步 + trace_id 回查）
        → 违规处置（拦截/下架/申诉）+ fail-open 降级
```

### 5. 可观测性：全链路 traceId + 三级审计

| 表 | 用途 |
|---|---|
| `audit_log` | 关键操作（登录/接取/审核/结算/封禁）|
| `login_log` | 登录记录（成功/失败原因）|
| `task_status_log` / `claim_status_log` | **状态变更全量留痕**（排查并发问题的唯一依据）|

每个请求通过 `TraceIdFilter` 注入 `traceId`（MDC），响应体与日志统一携带。

### 6. 数据一致性与兜底

- **结算快照**：`task_claim.reward` 在接取时固化，防止发布者改价影响已接取者
- **定时任务兜底**（5 类）：任务过期、接取超时、**审核超时自动通过**、内容安全回查、对账扫描
- 全部走 CAS 更新，**幂等可重跑**

### 7. 配置治理：拒绝"带默认密钥上线"

```java
// 生产环境缺密钥/弱密钥/使用开发默认值 → 直接拒绝启动
StartupValidator.afterPropertiesSet() {
    → 校验 JWT_SECRET(≥32字节)、PASSWORD_PEPPER、微信配置、OSS 配置...
}
```
多环境隔离：`application.yml`（通用）+ `-dev`（本地默认值）+ `-prod`（纯环境变量）。

### 8. 文件上传安全链

```
扩展名白名单(jpg/png/webp) → magic bytes 校验（读文件头，防伪装）
→ 大小限制(≤5MB) → 服务端随机文件名(biz/{type}/{yyyyMM}/{uuid}.ext)
→ 对象存储（本地/OSS，接口抽象，零业务改造切换）
```
> 实测：伪装成 jpg 的 exe 文件被拒绝（4001），6MB 文件被拒绝（4002）。

### 9. 把平台数据接成自然语言入口（AI 边车）

`module/ai` 把小程序问句转发给独立的 Python 边车（[TBagent](https://github.com/vienwinke/TBagent)），由它完成「意图路由 → NL2SQL / 知识库 RAG → 出表 + 出图 + 引用」：

- **身份不外包**：上行只有 `{session_id, question, client_msg_id}`，**不含 `user_id`**；openid → user_id / role 全在 Java 侧完成，边车只认内部 JWT（HS256 算法锁定、`aud=ai-sidecar`、有效期 ≤5 分钟）
- **吊销即时生效**：签发内部 JWT 时**沿用调用方当前会话的 `jti`**，登出/封禁/注销写 `token:blacklist:<jti>`，边车查的是同一个键 → **「踢人下线」对 AI 问答同样生效**（不是等 5 分钟过期）
- **只读硬约束**：边车用最小权限只读账号 + `SET SESSION TRANSACTION READ ONLY` + `max_execution_time`；所有 SQL 必须穿过唯一出口做行级隔离重写，普通用户拿不到 SQL 明文（只收到 `has_sql:true`）
- **流式与诚实降级**：SSE 逐帧转发（`meta → scope → route → sql → table → chart → delta → citations → guard → done`）；端到端 8s 预算耗尽时给 `error + done.timeout`，而不是 500 或假装成功

---

## 🛠️ 技术栈

| 类别 | 技术 | 版本 |
|---|---|---|
| 语言 / 运行时 | Java | 21 (LTS) |
| 框架 | Spring Boot | 3.5.16 |
| 构建 | Maven | 3.9 |
| ORM | MyBatis-Plus | 3.5.12 |
| 数据库 | MySQL | 8.4 (utf8mb4 / Asia/Shanghai) |
| 数据库迁移 | Flyway | V1~V11 |
| 缓存 / 限流 | Redis | 8.0 |
| 认证 | jjwt (JWT HS256) | 0.12.6 |
| 密码 | spring-security-crypto (BCrypt) | — |
| API 文档 | springdoc-openapi (Swagger UI) | 2.8.9 |
| 对象存储 | 阿里云 OSS SDK | 3.18.5 |
| 参数校验 | spring-boot-starter-validation | — |
| 健康检查 | spring-boot-starter-actuator | — |
| 前端 | 微信小程序（原生 WXML/WXSS/JS） | — |

---

## 🚀 快速开始

### 前置要求

JDK 21 · Maven 3.9+ · MySQL 8 · Redis · 微信开发者工具

### 1. 初始化数据库

```bash
# 只需建库；建表/索引/种子数据由 Flyway 在应用启动时自动迁移（V1~V11）
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS treatbord DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

> **单一真相源**：DDL 与种子数据的唯一权威来源是 `src/main/resources/db/migration/`（Flyway V1~V11）。
> 历史上手工维护的 `database/schema.sql` 已删除，避免与迁移脚本漂移。

### 2. 配置环境变量

```bash
cp .env.example .env.local
# 编辑 .env.local 填入 MySQL 账号密码
```

### 3. 启动后端

```bash
./start-dev.sh          # 自动加载环境变量 + 启动（含端口/依赖预检）
```

启动成功后：

| 地址 | 用途 |
|---|---|
| http://localhost:8080/swagger-ui/index.html | API 文档 |
| http://localhost:8080/actuator/health | 健康检查 |

### 4. 启动前端

微信开发者工具导入 `miniprogram/` 目录 → **详情 → 本地设置 → 勾选「不校验合法域名」** → 编译。

> 开发环境使用 mock 登录：任意 `code` 即可创建用户（如 `test1`）。

**详细启动/排障指南**：[docs/RUNBOOK.md](docs/RUNBOOK.md)　|　**生产部署（Docker）**：[deploy/README.md](deploy/README.md)

---

## 📁 项目结构

```
treatbord/
├── src/main/java/com/treatbord/
│   ├── common/              # 统一响应 Result、异常体系、脱敏工具
│   ├── config/              # Web/MyBatis/OpenAPI 配置、启动校验、traceId
│   ├── security/            # JWT、拦截器、限流、密码编码、上下文
│   └── module/              # 业务模块（按领域分包）
│       ├── auth/            # 认证（微信登录 / 账密登录）
│       ├── user/            # 用户与凭证设置
│       ├── task/            # 任务 + 接取 + 状态机
│       ├── submission/      # 凭证提交
│       ├── review/          # 审核 + 互评
│       ├── file/            # 文件上传（本地/OSS）
│       ├── notify/          # 站内通知
│       ├── report/          # 举报
│       ├── admin/           # 管理端
│       ├── settlement/      # 结算（状态位，预留支付）
│       ├── audit/           # 审计日志
│       └── schedule/        # 定时任务
├── src/main/resources/
│   ├── application*.yml     # 多环境配置
│   └── db/migration/        # Flyway 迁移脚本 V1~V11
├── miniprogram/             # 微信小程序（17 页面）
└── docs/                    # 设计文档（8 份）
```

---

## 📡 API 概览

共 **34 个接口**，统一响应体 `Result<T>`，`page/pageSize` 分页（上限 20）。

| 模块 | 接口 |
|---|---|
| 认证 | `POST /api/auth/login`（微信）、`POST /api/auth/account/login`（账密）、`POST /api/auth/logout` |
| 用户 | `GET /api/users/me`、`POST /api/users/me/credentials`、`DELETE /api/users/me`（注销）|
| 任务 | `GET/POST /api/tasks`、`GET /api/tasks/{id}`、`POST /api/tasks/{id}/cancel`、`GET /api/tasks/{id}/claims` |
| 接取 | `POST /api/tasks/{id}/claim`、`DELETE /api/claims/{id}`、`GET /api/me/claims` |
| 凭证 | `POST /api/claims/{id}/submit`、`GET /api/claims/{id}` |
| 审核 | `POST /api/claims/{id}/review` |
| 文件 | `POST /api/files`、`GET /files/biz/{type}/{yyyyMM}/{name}` |
| 通知 | `GET /api/notifications`、`POST /api/notifications/{id}/read`、`POST /api/notifications/read-all` |
| 互评 / 举报 | `POST /api/reviews`、`POST /api/reports` |
| 管理端 | `/api/admin/*`（任务下架、用户封禁/解封、举报处理、统计）|

完整接口规格见 [docs/API_DESIGN.md](docs/API_DESIGN.md)。

---

## 🗄️ 数据库设计

14 张表，全部含 `id / create_time / update_time / deleted`（逻辑删除），**不使用物理外键**（靠索引 + 应用层保证）。

| 分类 | 表 |
|---|---|
| 核心业务 | `user`、`task`、`task_claim`、`task_submission`、`file` |
| 互动 | `notification`、`review`、`report` |
| 结算 | `settlement`（预留：只跑状态位，不打款）|
| 审计 | `task_status_log`、`claim_status_log`、`audit_log`、`login_log` |
| 配置 | `app_config`（限流阈值/审核窗口等运行期可调）|

关键索引：`task(status, claim_deadline)`、`task_claim` 唯一索引 `(task_id, user_id)`、`notification(user_id, is_read, create_time)`。

详见 [docs/DB_DESIGN.md](docs/DB_DESIGN.md)。

---

## 📚 设计文档

| 文档 | 内容 |
|---|---|
| [API_DESIGN.md](docs/API_DESIGN.md) | 34 个接口的完整规格（参数/响应/错误码/业务规则）|
| [DB_DESIGN.md](docs/DB_DESIGN.md) | 14 张表设计、索引策略、设计决策记录 |
| [SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md) | 上架前安全审核：越权/注入/文件上传/密钥/可靠性 |
| [RUNBOOK.md](docs/RUNBOOK.md) | 启动/停止/排障手册（含压测/备份演练记录与运维动作） |
| [GO_LIVE_CHECKLIST.md](docs/GO_LIVE_CHECKLIST.md) | **上线清单**：所需文件、生产环境变量、部署产物（Docker 路线） |
| [CONSISTENCY_CHECKLIST.md](docs/CONSISTENCY_CHECKLIST.md) | 文档 ↔ 代码一致性核对表（数字全部实测） |
| [FINAL_REVIEW.md](docs/FINAL_REVIEW.md) | 最终代码总检报告（检查项、发现的 bug 与教训） |

---

## 🔒 安全与合规

按微信小程序上架标准实现：

- [x] **越权防护**：所有资源操作校验归属（IDOR）
- [x] **认证安全**：JWT + jti 黑名单（登出/封禁即时生效）
- [x] **密码安全**：BCrypt + pepper，旧哈希自动升级
- [x] **密钥管理**：全部环境变量注入，生产缺密钥拒绝启动
- [x] **免注入**：MyBatis 参数化查询，禁止 `${}` 拼接
- [x] **文件上传**：白名单 + magic bytes + 大小限制 + 随机文件名
- [x] **内容安全**：文本/图片检测 + 违规处置闭环
- [x] **隐私合规**：用户协议、隐私政策、注销入口（数据匿名化）
- [x] **日志脱敏**：openid/密码/token 不落日志
- [x] **限流**：分级限流防刷

---

## 📈 后续规划

- [ ] Maven 多模块拆分（common / pojo / server）+ 常量类体系
- [ ] 测试体系：JUnit5 + ArchUnit + Testcontainers（防超卖/状态机/越权）
- [ ] API 文档增强（Knife4j 中文文档 + 注解补全）
- [ ] 业务缓存（任务列表/详情 Redis 缓存）
- [ ] Druid 连接池 + SQL 监控
- [ ] 分布式锁（Redisson）支撑多实例定时任务
- [ ] 监控告警（Micrometer + Prometheus）
- [ ] 微信支付接入（当前为结算状态位）

---

## 📄 License

MIT


---

## 近期升级（2026-09-27）

### 技术栈新增

| 用途 | 技术 | 说明 |
|---|---|---|
| 文件访问授权 | HMAC-SHA256 签名 URL | `/files/**` 支持 `exp`+`sig` 校验（生产强制开启） |
| 指标监控 | Micrometer + Prometheus | `/actuator/prometheus`，含自定义业务指标 |
| 限流原子性 | Redis Lua 脚本 | `INCR` + 首次 `PEXPIRE` 原子执行，避免无 TTL 脏 key |
| 缓存 | Redis Cache-Aside | 任务列表/详情缓存，版本号失效 + 空值防穿透 + 随机 TTL |
| 登录风控 | Redis 计数 | IP 维度硬锁 + 账号维度只累计（防恶意锁定他人账号） |
| 慢查询/慢接口 | MyBatis 插件 + Servlet Filter | 阈值可配，日志只记 SQL id 不记参数 |

### 能力清单

| 能力 | 接口/位置 | 说明 |
|---|---|---|
| 文件签名 URL | `FileUrlSigner` | 读时签名；授权 = 上传者 + 接取双方（与凭证可见性一致） |
| 审计日志查询 | `GET /api/admin/audit-logs` | 支持 `action` / `userId` 筛选 + 分页 |
| 业务指标 | `BusinessMetrics` | 接取结果、限流拒绝、审核决策与时长、定时任务结果 |
| 慢接口日志 | `SlowRequestLoggingFilter` | 覆盖 `/files/**`、`/actuator/**` |
| 慢 SQL 日志 | `SlowSqlInterceptor` | 挂 `StatementHandler`（Executor 4 参 query 在本链路不触发） |

> 上线的检查项与结论见 [docs/FINAL_REVIEW.md](docs/FINAL_REVIEW.md) 与
> [docs/GO_LIVE_CHECKLIST.md](docs/GO_LIVE_CHECKLIST.md)；文档与代码一致性核对表见
> [docs/CONSISTENCY_CHECKLIST.md](docs/CONSISTENCY_CHECKLIST.md)。
