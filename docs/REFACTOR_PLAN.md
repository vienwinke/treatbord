# Treatbord 大修方案（A 架构重构 + B 技术栈升级）

> 状态：**待审核**。审核通过后按第 4 节批次逐批实施，每批可独立验收。
> 依据：2026-09-25 对当前代码/数据库/git 状态的实测扫描（非记忆）。

---

## 0. 现状诊断（实测依据）

| # | 问题 | 等级 | 实测证据 |
|---|---|---|---|
| 1 | **敏感字段经 API 泄露** | **P0 安全** | `GET /api/admin/users` 实测返回 `openid` 与 `passwordHash`（用户 wangmin/yw978712 的哈希明文可见）。根因：`AdminController` 返回 `PageResult<User>` 实体，User 无 `@JsonIgnore` |
| 2 | **模块边界形同虚设** | P1 架构 | 跨模块 `import com.treatbord.module.task.*` 达 **64 处**，`user` 20 处——模块间直接引用 Mapper/Entity，而非通过接口 |
| 3 | **Entity 直接出 Controller** | P1 架构 | `NotificationController`、`AdminController` 返回实体（14 个实体中 3 个外泄）|
| 4 | **事务边界不完整** | P1 可靠性 | 全项目仅 **7 处** `@Transactional`（5 个文件）；跨模块写操作（如审核→结算→通知）未统一收口 |
| 5 | **零测试代码** | P1 质量 | `src/test` 为空；防超卖/状态机/IDOR 三大核心无回归保护 |
| 6 | **密码哈希强度不足** | P1 安全 | 自研 `SHA-256 + salt + pepper`（无慢哈希），GPU 暴力破解成本低；应为 BCrypt/Argon2 |
| 7 | **前端页面冗余** | P2 维护 | 3 套登录相关页（`login` 已删、`login-preview` 预览、`login-v2` 正式）；页面规范不统一 |
| 8 | **工作区含无关产物** | P2 整洁 | `build_notebooks.py`（numpy 教程脚本，与本项目无关）、`.cloudbase/`（云托管配置）|
| 9 | **未提交改动堆积** | P2 流程 | 18 个已修改 + 11 个未跟踪（账号登录/限流/traceId 均未提交）|

---

## 1. 目标架构

**模块化单体（Modular Monolith）**——保持单应用部署，但建立可守护的模块边界：

```
┌─────────────────────────────────────────────────────┐
│  interfaces 层（Controller + DTO/VO）                │
│    ↓ 只依赖本模块 application                       │
├─────────────────────────────────────────────────────┤
│  application 层（应用服务：事务边界 + 用例编排）      │
│    ↓ 模块内依赖 domain/repository                   │
├─────────────────────────────────────────────────────┤
│  domain 层（实体 + 状态机 + 业务规则）               │
├─────────────────────────────────────────────────────┤
│  infrastructure 层（Mapper + 外部服务实现）          │
└─────────────────────────────────────────────────────┘

模块间通信：只允许通过 `module.<x>.api`（对外接口包）
禁止：跨模块 import mapper / entity / 内部 service
守护：ArchUnit 架构测试（CI 失败即拦截）
```

**不做的事**（避免过度设计）：微服务拆分、重度 DDD 分层（聚合根/领域事件总线）、CQRS 读写分离。

---

## 2. A 架构重构（4 个工作流）

### A1. 出口隔离（VO 化）— P0，优先做

| 项 | 内容 |
|---|---|
| 目标 | 所有 Controller 出入参 **只用 DTO/VO**，Entity 不出 Service 层 |
| 范围 | `AdminController`（User/Report）、`NotificationController`、`SubmissionController`（Map→VO）|
| 手法 | 新增 `UserVO`（去掉 openid/passwordHash/unionid）、`NotificationVO`、`ReportVO`；MapStruct 自动映射 |
| 验收 | 全接口回归：响应中不出现 `openid`、`passwordHash`、`unionid`、`deleted` |

### A2. 模块边界治理 — P1

| 项 | 内容 |
|---|---|
| 目标 | 跨模块引用归零（改为接口调用）|
| 手法 | 每模块暴露 `api` 包子包（如 `task.api.TaskQueryApi`），其他模块只依赖接口；实现类包内私有 |
| 重点 | `review`/`submission`/`admin`/`notify` 对 `task` 的 64 处依赖拆解为 3~5 个查询接口 |
| 守护 | ArchUnit 规则：`module.a..` 不得访问 `module.b..internal..` |

### A3. 事务边界收敛 — P1

| 项 | 内容 |
|---|---|
| 目标 | 跨表/跨模块写操作的事务边界统一在 application 服务，禁止 Controller/Mapper 层开事务 |
| 手法 | 梳理 7 处现有 `@Transactional`，补齐缺失边界（审核→结算→通知、接取→扣减→日志）|
| 验收 | 并发/异常注入测试：任一步失败全回滚，无中间态 |

### A4. 领域规则收敛 — P2（可选）

| 项 | 内容 |
|---|---|
| 目标 | 状态机、信用分规则、超时规则从 Service 收敛到 domain |
| 手法 | `TaskStatus`/`ClaimStatus` 迁移表保留，新增 `TaskDomainService` 承载不变量校验 |

---

## 3. B 技术栈升级（3 批）

### B-P0 安全批（必做）

| 项 | 现状 | 升级为 | 依赖 | 风险 |
|---|---|---|---|---|
| B1 密码哈希 | 自研 SHA-256+盐+pepper | **BCrypt**（strength 10~12）| `spring-security-crypto`（轻量，**不引入完整 Security 框架**）| 低；需兼容存量哈希（见 §5 数据迁移）|
| B2 敏感字段保护 | 无 | `@JsonIgnore` + VO 隔离 + 日志脱敏 | 无 | 低 |
| B3 密钥治理 | 部分环境变量 | 全量环境变量 + 启动校验（缺失即拒启）| 无 | 低 |

### B-P1 效率批

| 项 | 内容 | 说明 |
|---|---|---|
| B4 Druid | 连接池 + `/druid/*` SQL 监控面板 | 替换 HikariCP；上次 C 方案未实施 |
| B5 Knife4j | `knife4j-openapi3-ui:4.5.0`（纯 UI 包）| 与 springdoc 2.8.9 零冲突，提供 `/doc.html` 中文文档 |
| B6 MapStruct | DTO/VO/Entity 映射生成器 | 消除手写 `from()` 转换；与 Lombok 需配置 annotationProcessor 顺序 |
| B7 测试体系 | JUnit5 + **ArchUnit** + **Testcontainers** | 覆盖三大核心：防超卖并发、状态机非法流转、IDOR 越权 |

### B-P2 生产化批（后置）

| 项 | 内容 |
|---|---|
| B8 Redisson | 分布式锁（定时任务多实例防重入）；AGENTS §3 已列"备而不用"，多实例时启用 |
| B9 对象存储 | `StorageService` 新增 MinIO/OSS 实现（接口已就绪，零业务改造）|
| B10 内容安全 | 微信 `msgSecCheck` + `mediaCheckAsync` 真实接入（替换占位实现）|
| B11 可观测性 | Micrometer + Prometheus 指标；慢 SQL/错误率告警 |

---

## 4. 实施路线图（5 批，每批可独立验收）

| 批次 | 内容 | 改动量 | 验收标准 |
|---|---|---|---|
| **第 1 批** | A1 出口隔离 + B1 BCrypt + B2 敏感字段 + B3 密钥校验 | ~20 文件 | 全接口响应无敏感字段；存量密码可登录（兼容期）；新密码为 BCrypt |
| **第 2 批** | B4 Druid + B5 Knife4j + B6 MapStruct | ~10 文件 | `/druid` 面板可用（SQL 统计）；`/doc.html` 中文文档可用；编译通过 |
| **第 3 批** | A2 模块边界 + A3 事务收敛 | ~40 文件 | ArchUnit 通过；跨模块直接依赖归零；异常注入测试全回滚 |
| **第 4 批** | B7 测试体系 | +15 测试文件 | 三大核心测试通过；`mvn test` 绿灯 |
| **第 5 批** | 前端清理 + B8~B11 | 视范围 | 冗余页面清理；监控/存储/内容安全按需接入 |

> 建议节奏：第 1 批（安全）→ 第 2 批（工具）→ 第 3 批（结构）→ 第 4 批（保障）→ 第 5 批（生产化）。
> **前置动作**：先提交当前未提交改动，固化基线（否则重构 diff 混杂）。

---

## 5. 影响面与数据迁移

| 项 | 影响 | 处理 |
|---|---|---|
| **密码哈希迁移** | 存量 `password_hash`（SHA-256 格式 `salt:hash`）| **渐进式**：登录校验兼容两种格式，验证成功后自动升级为 BCrypt（无需强制重置）|
| **接口响应结构** | 去掉敏感字段属**破坏性变更** | 前端 `api.js` 无字段依赖（已核对），影响面小；管理端页面需回归 |
| **模块边界改造** | 触及 ~40 文件 | 分批提交，每批保证可编译可运行 |
| **Druid 替换 HikariCP** | 配置项变更（`spring.datasource.druid.*`）| 保留原 Hikari 配置注释，便于回滚 |
| **MapStruct 引入** | 编译期注解处理 | 与 Lombok 顺序配置：`lombok-mapstruct-binding` |
| **数据库** | 本次大修**不新增业务表**；若密码迁移需版本列，走 Flyway V5 | 迁移脚本纳入版本管理 |

**回滚策略**：每批一个 git commit；Druid/MapStruct 等可单点回退（配置/依赖级）；模块边界改造若失控，可回退到批次前 tag。

---

## 6. 风险清单

| 风险 | 概率 | 缓解 |
|---|---|---|
| 模块边界改造面大引发回归 | 中 | 先补测试（第 4 批提前）或先做接口契约测试；分批提交 |
| 密码兼容期逻辑漏洞 | 低 | 双格式校验单测覆盖；升级后立即覆写 |
| MapStruct + Lombok 注解处理冲突 | 中 | 固定 annotationProcessor 顺序，编译验证 |
| Druid 监控面板暴露 | 低 | 面板加账号密码（环境变量）+ 生产限内网 IP |
| 大修期间业务中断 | 低 | 分支持续可运行；每批结束跑全链路冒烟 |

---

## 7. 待拍板（5 条）

| # | 问题 | 我的建议 |
|---|---|---|
| 1 | 是否引入完整 Spring Security？ | **不引入**，只用 `spring-security-crypto` 做 BCrypt（保持现有轻量拦截器）|
| 2 | 架构改造深度 | **务实版模块化单体**（api 包隔离 + ArchUnit），不做重度 DDD |
| 3 | 密码迁移方式 | **渐进式兼容升级**（不强制用户重置密码）|
| 4 | 前端是否上 uni-app/Taro | **保持原生**，只清理冗余页面 + 统一规范 |
| 5 | 实施顺序 | 按第 4 节 5 批推进，**先第 1 批（安全）** |

---

## 8. 立即可以做的事（审核通过后第一步）

1. **提交当前未提交改动**（固化基线）
2. **第 1 批开工**：UserVO 隔离 + BCrypt + `@JsonIgnore` + 密钥启动校验
3. 完成后跑全接口敏感字段扫描 + 登录回归（含存量密码）

---

_本方案为审核稿；确认或修改第 7 节后即可开工。_
