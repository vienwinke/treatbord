# Treatbord 标准化改造意见（对标苍穹外卖）

> 状态：**执行中**——P0（上线必须）6 项已全部完成并通过自测（见 §3 勾选）；下一步 P1 标准化。
> 参照基准：[苍穹外卖 sky-take-out 工程结构](https://github.com/dream-kaii/sky-take-out)（`sky-common` / `sky-pojo` / `sky-server` 三模块）与 [包结构规范](https://blog.csdn.net/2401_85190702/article/details/150149902)。
> 关联文档：`docs/REFACTOR_PLAN.md`（A 架构重构 + B 技术栈升级）——本文件是其**标准化落地细化**，两者合并执行，见 §6。

---

## 1. 对标基准：苍穹外卖的标准结构

```
sky-take-out/                     ← Maven 父工程（聚合 + 统一依赖版本）
├── sky-common/                   ← 公共模块
│   └── com/sky/
│       ├── constant/             ← 常量类（MessageConstant/StatusConstant/JwtClaimsConstant/AutoFillConstant）
│       ├── context/              ← BaseContext（ThreadLocal 上下文）
│       ├── enumeration/          ← 枚举
│       ├── exception/            ← 自定义异常体系
│       ├── json/                 ← Jackson 序列化处理（LocalDateTime 等）
│       ├── properties/           ← @ConfigurationProperties 配置类
│       ├── result/               ← Result / PageResult 统一响应
│       └── utils/                ← 工具类（JwtUtil / AliOssUtil / HttpClientUtil）
├── sky-pojo/                     ← 数据模型模块
│   └── com/sky/{dto, entity, vo} ← 入参 DTO / 实体 / 出参 VO 分离
└── sky-server/                   ← 服务模块
    └── com/sky/
        ├── annotation/           ← 自定义注解（@AutoFill）
        ├── aspect/               ← 切面（AutoFillAspect 自动填充）
        ├── config/               ← 配置类（WebMvc/Redis/OSS）
        ├── controller/{admin,user} ← 双端 Controller 分包
        ├── handler/              ← GlobalExceptionHandler
        ├── interceptor/          ← JWT 拦截器（admin/user 各一）
        ├── mapper/               ← MyBatis Mapper
        ├── service/(impl)        ← 业务层
        ├── task/                 ← 定时任务
        └── websocket/            ← WebSocket 推送
```

---

## 2. 差距分析（当前 vs 苍穹标准）

| # | 维度 | 苍穹标准 | 当前 Treatbord | 差距 |
|---|---|---|---|---|
| 1 | **Maven 模块** | 3 模块（common/pojo/server）| **单模块** | ❌ 大 |
| 2 | **包结构** | 细粒度技术分包（constant/context/enumeration/…）| `common`/`config`/`security` 粗分包 | ⚠️ 中 |
| 3 | **常量管理** | `MessageConstant` 集中 | **消息硬编码在各 Service**（如 `"任务不存在"`）| ❌ 中 |
| 4 | **多环境配置** | yml + dev/prod 分文件 | **单一 application.yml** | ❌ **上线必须** |
| 5 | **API 文档注解** | @Api/@ApiOperation 全套 + Knife4j | **零注解**（自动生成的标题是 `list`/`detail`）| ❌ 中 |
| 6 | **DTO/VO/Entity 分离** | pojo 模块三层分离 | 已有 14 个 DTO，但 **3 个 Controller 仍返回 Entity**（P0 泄露）| ⚠️ 中 |
| 7 | **统一响应** | Result / PageResult | ✅ 已有 | ✅ 达标 |
| 8 | **全局异常** | BaseException + Handler | ✅ BusinessException + Handler | ✅ 达标 |
| 9 | **上下文** | BaseContext(ThreadLocal) | ✅ UserContext | ✅ 达标 |
| 10 | **JWT + 拦截器** | 双端拦截器 | ✅ 单拦截器 + @RequireAdmin（更简洁）+ jti 黑名单 | ✅ 超标 |
| 11 | **自动填充** | @AutoFill + AOP | ✅ MyBatis-Plus MetaObjectHandler（等效）| ✅ 达标（实现不同）|
| 12 | **定时任务** | task 包 + @Scheduled | ✅ ScheduledTasks（含 5 类扫描）| ✅ 达标 |
| 13 | **对象存储** | 阿里云 OSS 工具类 | 本地磁盘 + StorageService 抽象（**无 OSS 实现**）| ⚠️ **上线必须** |
| 14 | **业务缓存** | Redis 缓存菜品/套餐 | Redis 仅用于黑名单/限流，**业务无缓存** | ⚠️ 中 |
| 15 | **日志规范** | @Slf4j 分层日志 | ✅ 更强（traceId MDC 贯穿 + 脱敏待补）| ✅ 超标 |
| 16 | **参数校验** | @Validated + 分组 | ✅ 基础校验（无分组）| ⚠️ 小 |
| 17 | **数据库规范** | 逻辑删除/索引 | ✅ 更严（乐观锁/状态日志/审计表）| ✅ 超标 |
| 18 | **测试** | 无（教学项目）| **无** | ⚠️ 上线需要 |
| 19 | **WebSocket** | 有（来单提醒）| 无（蓝图明确排除）| ⚪ 不需要 |
| 20 | **微信支付** | 有 | 无（蓝图明确排除，保留结算状态位）| ⚪ 不需要 |

**结论**：你的项目在**安全/数据/可观测**维度已**超过**苍穹（jti 黑名单、限流、traceId、审计表、乐观锁）；差距集中在 **工程结构标准化**（模块拆分、常量、配置分环境）和 **生产化配套**（OSS、缓存、文档注解、测试）。

---

## 3. 修改意见（分级）

### 🔴 P0 — 上线必须（不改不能上线）

| # | 修改项 | 具体做法 | 工作量 |
|---|---|---|---|
| ✅ P0-1 | **敏感字段泄露修复** | UserVO/NotificationVO/ReportVO 隔离 + `@JsonIgnore` + 日志脱敏 | 0.5 天 |
| ✅ P0-2 | **多环境配置** | `application.yml` + `application-dev.yml` + `application-prod.yml`；Maven profile 切换；生产密钥全环境变量 + 启动校验 | 0.5 天 |
| ✅ P0-3 | **对象存储 OSS** | `StorageService` 新增 `AliOssStorageService`（复用接口，零业务改造）；保留本地实现用于开发 | 0.5 天 |
| ✅ P0-4 | **密码哈希升级 BCrypt** | `spring-security-crypto` 替换自研 SHA-256；渐进式兼容迁移 | 0.5 天 |
| ✅ P0-5 | **内容安全真实接入** | 微信 `msgSecCheck`（文本）+ `mediaCheckAsync`（图片）替换占位实现 | 1 天 |
| ✅ P0-6 | **合规页面** | 用户协议 + 隐私政策页（小程序端）+ 微信隐私保护指引配置 | 1 天（含前端）|

### 🟡 P1 — 标准化（对标苍穹，强烈建议）

| # | 修改项 | 具体做法 | 工作量 |
|---|---|---|---|
| P1-1 | **Maven 三模块拆分** | `treatbord-common` / `treatbord-pojo` / `treatbord-server`；父 pom 统一依赖管理 | 1.5 天 |
| P1-2 | **包结构规范化** | common 内细化 `constant/context/enumeration/exception/json/properties/result/utils`；server 内 `annotation/aspect/handler/interceptor/task` | 1 天（随 P1-1 一并做）|
| P1-3 | **常量类体系** | 抽 `MessageConstant`（全部提示语）、`StatusConstant`、`CacheConstant`，消除硬编码 | 0.5 天 |
| P1-4 | **API 文档注解 + Knife4j** | `@Tag`/`@Operation`/`@Schema` 全量补注解；加 `knife4j-openapi3-ui` 提供中文 `/doc.html` | 1 天 |
| P1-5 | **业务缓存** | 任务列表/详情 Redis 缓存（先删缓存再写库 + TTL 可配），对标苍穹的菜品缓存 | 1 天 |
| P1-6 | **测试体系** | JUnit5 + ArchUnit（架构守护）+ Testcontainers；覆盖防超卖/状态机/IDOR | 1.5 天 |
| P1-7 | **前端规范化** | 清理冗余页（login-preview）；统一页面模板与色板；补齐空页面 | 1 天 |

### 🟢 P2 — 增强（可上线后迭代）

| # | 修改项 | 说明 |
|---|---|---|
| P2-1 | Druid 连接池 + SQL 监控 | 替换 HikariCP，提供 `/druid` 面板 |
| P2-2 | MapStruct 映射 | 消除手写 `from()` 转换 |
| P2-3 | Redisson 分布式锁 | 多实例定时任务防重入 |
| P2-4 | Micrometer + Prometheus | 指标监控告警 |
| P2-5 | CI/CD | GitHub Actions 构建 + 镜像 |
| P2-6 | 日志采集与保留策略 | 防磁盘打满 |

---

## 4. 建议的目标结构（推荐方案）

**关键决策**：苍穹用「技术分层」（controller/service/mapper 平铺），你的项目现在用「业务模块分包」（module/xxx）。**推荐混合**——保留业务内聚，同时获得模块化标准：

```
treatbord/                              ← 父工程（聚合 + 依赖版本管理）
├── pom.xml
├── treatbord-common/                   ← 公共模块（无业务依赖）
│   └── com/treatbord/common/
│       ├── constant/                   ← MessageConstant / StatusConstant / CacheConstant
│       ├── context/                    ← UserContext（ThreadLocal）
│       ├── enumeration/                ← ResultCode / TaskStatus / ClaimStatus
│       ├── exception/                  ← BusinessException（+ 子类）
│       ├── json/                       ← Jackson 配置（LocalDateTime 序列化）
│       ├── properties/                 ← JwtProperties / WxProperties / StorageProperties
│       ├── result/                     ← Result / PageResult
│       └── utils/                      ← JwtUtil / MaskUtil（脱敏）/ DateUtil
│
├── treatbord-pojo/                     ← 数据模型（仅依赖 common）
│   └── com/treatbord/pojo/
│       ├── dto/                        ← 全部入参
│       ├── entity/                     ← 14 个实体
│       └── vo/                         ← 全部出参（含 UserVO 等新增）
│
└── treatbord-server/                   ← 服务（依赖 common + pojo）
    └── com/treatbord/
        ├── TreatbordApplication.java
        ├── annotation/                 ← @RequireAdmin / @RateLimit
        ├── aspect/                     ← 操作日志切面（可选）
        ├── config/                     ← MybatisPlus / Web / OpenApi / Redis / Storage / Druid
        ├── handler/                    ← GlobalExceptionHandler
        ├── interceptor/                ← AuthInterceptor / RateLimitInterceptor
        ├── task/                       ← ScheduledTasks（定时任务）
        └── module/                     ← 业务模块（保留内聚）
            ├── auth/  user/  task/  submission/  review/
            ├── report/  notify/  file/  admin/  settlement/  audit/
            └── (每模块内 controller / service / mapper)
```

**为什么不完全照搬苍穹**：
- 苍穹的 `controller/admin` + `controller/user` 双端分包适合"管理端+用户端"两套 API；你的 admin 已独立成模块，其余按业务分包更内聚。
- 苍穹的 `@AutoFill` + AOP 与你的 `MetaObjectHandler` 功能等效，**不必为对标而改**（避免无谓回归）。
- WebSocket / 微信支付在蓝图 §2 明确排除，不纳入。

---

## 5. 可上线检查清单（技术 + 合规）

### 技术侧
- [ ] 多环境配置（dev/prod 分离，生产密钥零硬编码 + 启动校验）
- [ ] 敏感字段保护（VO 隔离 + 日志脱敏）
- [ ] 密码哈希 BCrypt
- [ ] 对象存储 OSS（替代本地磁盘）
- [ ] 内容安全（文本 + 图片）
- [ ] 业务缓存（任务列表/详情）
- [ ] 测试覆盖核心路径（防超卖/状态机/IDOR）
- [ ] API 文档完整（中文注解 + Knife4j）
- [ ] 限流生效（已 ✅）
- [ ] 可观测（health ✅ / traceId ✅ / 指标告警 ⬜）
- [ ] 压测（并发接取无超卖 —— 已有功能验证，需压测数据）
- [ ] 数据库备份 + 恢复演练
- [ ] HTTPS + 域名备案 + 合法域名配置

### 合规侧（微信上架）
- [ ] 类目资质确认（**上架硬门槛，最先做**）
- [ ] 用户协议 + 隐私政策页面
- [ ] 微信隐私保护指引配置
- [ ] 注销入口（接口 ✅ / 页面待补）
- [ ] 内容安全检测（微信要求 UGC 全覆盖）

---

## 6. 与 REFACTOR_PLAN 的关系（合并执行）

两份文档高度重叠，**建议合并为一条路线**：

| 本文件 | REFACTOR_PLAN | 合并后 |
|---|---|---|
| P0-1 敏感字段 | A1 出口隔离 | 同一件事 |
| P0-2 多环境 | B3 密钥治理 | 合并 |
| P0-4 BCrypt | B1 密码哈希 | 同一件事 |
| P1-1 三模块 | A2 模块边界 | **合并为"模块化 + 三模块拆分"一次做** |
| P1-4 文档注解 | B5 Knife4j | 合并 |
| P1-6 测试 | B7 测试体系 | 同一件事 |
| P2-1 Druid | B4 Druid | 同一件事 |

**统一执行顺序（建议）**：

| 批次 | 内容 | 工期 |
|---|---|---|
| **0** | 提交当前未提交改动（固化基线）| 0.5h |
| **1** | P0 全部（敏感字段 + 多环境 + OSS + BCrypt + 内容安全 + 合规页）| 3~4 天 |
| **2** | P1-1/2/3（三模块拆分 + 包结构 + 常量类）| 2~3 天 |
| **3** | P1-4/5（API 注解 + Knife4j + 业务缓存）| 2 天 |
| **4** | P1-6/7（测试体系 + 前端规范化）| 2.5 天 |
| **5** | P2（Druid/MapStruct/Redisson/监控/CI-CD）| 按需 |

> 合计 P0+P1 约 **10~12 人天**。

---

## 7. 待拍板（4 条）

| # | 问题 | 我的建议 |
|---|---|---|
| 1 | Maven 拆几个模块？ | **3 个**（common/pojo/server），对标苍穹；不拆 4+ 个（过度）|
| 2 | server 内包结构 | **业务模块分包**（保留现状内聚），不照搬苍穹的技术平铺 |
| 3 | 是否保留 MetaObjectHandler | **保留**（与苍穹 AOP 等效，无需为对标而改）|
| 4 | 前端是否也按苍穹小程序规范重构 | **部分**：清理冗余 + 统一模板；不重写架构 |

---

_审核确认第 7 节 + 执行顺序后即可开工。_
