# Treatbord 开发日志与记忆（Session Log）

> 本文件记录整个开发会话的进度、决策、环境配置、踩坑与当前状态。
> 用于跨会话恢复上下文。配套文档：`AGENTS.md`（方案）、`docs/DB_DESIGN.md`、`docs/API_DESIGN.md`、`docs/ROADMAP.md`、`docs/SECURITY_REVIEW.md`。

---

## 一、项目状态总览（截至当前）

| 项 | 状态 |
|---|---|
| 后端全部接口（P0-P4）| ✅ 完成并通过端到端自测 |
| 数据库（14 表 + V3 迁移）| ✅ 完成 |
| 小程序前端（7 页面）| ✅ 完成，模拟器已渲染成功 |
| 演示数据 | ✅ task/claim/notification 有数据 |
| 后端运行 | ✅ 8080 端口运行中 |

---

## 二、环境配置（重要，重启后要恢复）

### 技术栈
- Java 21 + Spring Boot 3.5.16 + Maven（阿里云镜像 `settings-mirror.xml`）
- MySQL 8.4（WSL 内，127.0.0.1:3306）
- Redis（WSL 内，127.0.0.1:6379，登出黑名单/封禁踢人用）

### 密钥/配置
- `.env.local`：MYSQL_PASSWORD（app_user）、MYSQL_MIGRATE_*（db_migrate 迁移账号）——**已 gitignore，不入库**
- JWT secret：`application.yml` 默认开发值，生产必须环境变量注入

### 启动后端
```bash
cd /home/KeYa/Project/treatbord
set -a && source .env.local && set +a
mvn -s settings-mirror.xml clean compile -DskipTests   # 改代码后先 clean（避 stale class）
mvn -s settings-mirror.xml spring-boot:run
```

> ⚠️ **前端 baseUrl（重要）**：`miniprogram/app.js` 的 baseUrl 已从 `127.0.0.1` 改为 **WSL 直连 IP `http://172.28.58.76:8080/api`**（Windows 侧 localhost 转发失效，`ERR_CONNECTION_REFUSED`）。
> **WSL 重启后 IP 会变**：`hostname -I` 查最新值再改回 app.js 第 7 行。

> ⚠️ **坑**：Maven 本地仓库在 `.m2/`（HOME 只读），settings-mirror.xml 指向它 + 阿里云镜像（否则 Flyway 拉私有仓库 401）。

### 数据库账号
| 账号 | 权限 | 用途 |
|---|---|---|
| `app_user` | treatbord 库 DML | 应用连接 |
| `db_migrate` | treatbord 库全权限 | Flyway 迁移 |
| root | auth_socket（仅本机）| 运维 |

### WSL 提权方式（无 sudo 密码）
```bash
/mnt/c/Windows/System32/wsl.exe -u root -e bash -c '...'
# 用于装 MySQL/Redis/字体、停服务、看表
```

---

## 三、进度记录（按阶段）

### P0：骨架 + 认证 ✅
- 登录（mock 微信 code）、登出（jti 黑名单）、GET/DEL /api/users/me、审计（audit_log/login_log）
- JWT(HS256, 含 userId/jti/role/status) + AuthInterceptor + UserContext + @RequireAdmin
- Flyway baseline→v2（config 种子）
- 自测：登录/401/参数校验/黑名单/注销匿名化 全过

### P1：任务 + 接取 ✅
- 任务发布/列表(惰性 EXPIRED)/详情/取消；接取防超卖（原子 SQL + 乐观锁 + 唯一索引）
- 状态机：TaskStatus/ClaimStatus 枚举 + 迁移 map
- **修复的 bug**：原子扣减原本只允许 OPEN 状态 → 改为 IN ('OPEN','IN_PROGRESS')，否则任务变进行中后名额无法继续扣
- 自测：并发抢名额零超卖、防自接、防重复、IDOR 403 全过

### P2：凭证 + 通知 + 审核 + 定时任务 ✅
- submit（CLAIMED→SUBMITTED + 覆盖提交 + 48h 审核窗口）、GET /claims/{id}
- 通知列表/已读/read-all + 触发点
- 审核 approve/reject + 任务收尾（REVIEWING→SETTLED + 结算生成）
- 定时任务：过期/接取超时/审核兜底/对账（@Scheduled，CAS 幂等）
- **修复的 bug**：覆盖提交原返回 409 → 改为 SUBMITTED 状态可覆盖（跳过重复流转）
- **修复的 bug**：类名 TaskScheduler 与 Spring Boot 自动配置 bean 冲突 → 改名 ScheduledTasks

### P3：文件 + 互评 + 举报 ✅
- 文件上传安全链：白名单(jpg/png/webp) + magic bytes + ≤5MB + 随机文件名 + sec_status
- 互评（唯一约束防重复）、举报
- **修复的 bug**：magic bytes 校验把 PNG 当双段签名 → 改回单段；V3 迁移给 file 表加 sec_status 列
- 自测：真图通过/伪装图拒绝 4001/超限 4002/防重复互评 409

### P4：管理端 + 结算 ✅
- 下架/封禁(踢下线 jti 黑名单)/解封/举报处理/统计，@RequireAdmin 隔离
- 结算状态位（结算快照，MVP 不打款）
- 自测：普通用户访问 403、封禁即踢下线、解封可再登录

### P5：小程序前端 ✅
- 7 页面：login/index/detail/publish/my-claims/submit/review + utils（request/api）
- 语言：原生微信小程序（WXML+WXSS+JS+JSON），非框架
- **配套后端新增接口**：GET /api/tasks/{id}/claims（发布者查看接取+凭证，供审核页用）
- 模拟器已渲染成功（任务大厅能拉数据）

### P5.1：补齐缺口页面 ✅（本次会话）
- 新增 4 页：notifications（通知列表/单条已读/全部已读/未读角标）、report（举报，targetType/targetId 参数化）、peer-review（互评：星级评分 1-5 + 内容，claim 双方均可进）、admin（管理后台：统计/举报处理/用户封禁解封/任务下架，role=ADMIN 隔离）
- 入口接线：my-claims 加「通知（未读红点）」+ ADMIN 显示「管理后台」；接取 APPROVED 加「互评」；review 页 APPROVED 接取加「互评该接取者」；detail 页加「举报该任务」
- utils/api.js 增补：readAll/unreadCount + 管理端 8 个接口
- 校验：全部 JS（node --check）与 JSON/WXML 标签闭合通过
- ⚠️ 待办：需在微信开发者工具中编译验证新页面（模拟器已渲染过旧 7 页）

### P5.2：路由/接口全量核验 + 修复（本次会话）
- **核验**：8 处页面跳转 vs app.json 11 页全命中；23 个前端接口路径 vs 后端 Controller 映射逐一对齐；PageResult{list,total}、通知 isRead 筛选契约一致
- **修复 bug（IDOR/越权）**：`GET /api/me/tasks` 原来误调 `taskService.list()`（返回全量任务）→ 改为 `taskService.myTasks(userId,…)`；实测 xiaomei 仅见自己 2 个任务、xiaoming 0 条 ✅
- **演示账号修正**：数据库里 xiaomei(id=1) 原为 role=0，与 LOG 记载的 ADMIN 不符 → `UPDATE user SET role=1 WHERE id=1`，管理端接口实测通过
- **冒烟测试全部通过**：通知未读数 / 举报提交 / 管理统计·举报列表·处理·用户列表 / 互评（xiaoming→xiaomei score5）/ 非 ADMIN 调 admin 403 拦截
- 后端已由会话拉起（bash-1 job，8080）；冒烟产生演示数据：举报 2 条（1 条已处理）、互评 1 条

### P5.3：游客可浏览详情（本次会话）
- `api.taskDetail` 去掉 `auth:true` → 游客可看任务详情（后端本就匿名放行 GET /api/tasks/{id}）
- `detail.js` 增加 `ensureLogin()`：接取/举报/提交前未登录 → 弹窗引导登录
- `login.js` 支持 `?redirect=` 回跳：从详情页被引导登录后，登录成功 `reLaunch` 回原任务页（原 redirectTo 会关闭详情页，故用显式回跳）

### P5.4：登录页 UI 改版（本次会话）
- 重做 login 页：渐变品牌区（主色 #4A90D9） + 居中白卡片登录面板 + 底部版本号；**移除旧的 `top:347rpx` 内联 hack**，改 flex 垂直居中
- 协议入口可点击：《用户协议》《隐私政策》弹窗（占位文案，MVP；上线前按 SECURITY_REVIEW §2 补全完整条款）
- 保留 P5.3 的 redirect 回跳逻辑；登录按钮 loading 态带文案

### P5.5：修复「网络错误」（本次会话）
- **原因**：`project.config.json` 未设 `urlCheck:false`，开发者工具默认合法域名校验拦截 `http://127.0.0.1:8080`（http+IP 非合法域名）→ `fail url not in domain list`
- 修复：`project.config.json` 加 `"urlCheck": false`；`utils/request.js` fail 分支按 errMsg 分类提示（域名拦截/超时/网络错误），联调自愈
- 说明：生产环境仍必须 https 域名 + 备案 + 「request 合法域名」配置，`urlCheck:false` 仅限开发

---

## 四、当前运行中的服务与 Data

- 后端 8080（Spring Boot）+ Redis + MySQL 均运行
- 演示账号（登录 code）：
  - `xiaomei` → 发布者（昵称"小美"，role=ADMIN）
  - `xiaoming` → 接取者（昵称"小明"）
  - 普通测试：任意 code（如 `mp-check`）→ mock 建新用户
- 演示任务：帮忙拍产品照(OPEN)、周末取快递(IN_PROGRESS 待审核)、翻译资料(SETTLED)

---

## 五、踩坑记录（复盘）

1. **WSL 提权**：当前无 sudo 密码（no new privileges），用 `wsl.exe -u root` 绕过
2. **HOME 只读**：Maven .m2 放工作区 + 阿里云镜像 settings
3. **Maven 私有仓库 401**：flyway 依赖拉 GitHub Packages，用 mirrorOf=* 镜像掉
4. **MySQL bind 127.0.0.1**：Windows 侧访问需 localhost 转发（WSL2）；Spring Boot 监听 *:8080 则通
5. **Project bean 名冲突**：TaskScheduler 占用了 spring 的 taskScheduler
6. **stdin 冲突**：curl | python3 <<EOF 时 stdin 被 heredoc 占 → 先 curl -o 文件
7. **WXML 无过滤器**：`| lower` 不被支持，需 JS 预计算 tagClass
8. **工具模拟器**：`simulator launch failed` → 重启工具/清缓存；`App is not defined` → 不能用 node 跑，必须工具编译
9. **前后端联通**：小程序遇到"网络错误"通常在「详情→本地设置→不校验合法域名」未勾选
10. **新增坑**：`ERR_CONNECTION_REFUSED`（127.0.0.1:8080）≠ 域名校验，是 **Windows→WSL2 localhost 转发失效**；解法：baseUrl 改用 WSL eth0 IP（`hostname -I` 查询），且必须 `hostname -I` 首个地址（wsl.localhost 映射同名）

---

## 六、待办 / 下一步

- [ ] 微信开发者工具「详情→本地设置→不校验合法域名」勾选后前端全流程验证（含新增 4 页：通知/举报/互评/管理后台）
- [ ] 若真机预览：改 `miniprogram/app.js` 的 baseUrl 为局域网 IP
- [ ] 核心单测（防超卖/状态机/IDOR）—— ROADMAP 横切任务
- [ ] Redis 限流接入（登录/接取/提交/上传分级）
- [ ] 内容安全真实接入（微信 msgSecCheck / mediaCheckAsync，当前占位）
- [ ] 生产配置：JWT secret 环境变量、HTTPS、域名

---

## 七、客户端体验方法

1. 微信开发者工具导入 `\\wsl.localhost\Ubuntu\home\KeYa\Project\treatbord\miniprogram`
2. **必须勾选「详情→本地设置→不校验合法域名」**（后端是 http://127.0.0.1:8080）
3. 点编译 / 预览
4. 底层用的是 JS 引擎：iOS=JavaScriptCore、Android=V8、模拟器=Chromium

---

# 📅 2026-09-25 工作记录：P0 上线必备改造

## 一、背景

会话开始时对项目做全面盘点，发现项目较上次记录已大幅演进：
- 新增账密登录（AccountLoginRequest/PasswordEncoder/V4 迁移）
- Redis 固定窗口限流（RateLimitService/Interceptor）
- 全链路 traceId（TraceContext/TraceIdFilter）
- actuator 健康检查、16 个小程序页面（含 admin/notifications/peer-review/report/credentials/order-list）

盘点同时发现 **18 个已修改 + 11 个未跟踪文件未提交**，故先固化基线（commit `1284c29`）。

## 二、解决的问题（P0 六项，全部完成并自测）

| 项 | 问题 | 解决 | 验证 |
|---|---|---|---|
| P0-1 | 管理端接口**明文泄露** openid + passwordHash | UserVO/NotificationVO/ReportVO 隔离 + User 加 @JsonIgnore + MaskUtil 日志脱敏 | 实测三接口敏感字段归零 |
| P0-2 | 单文件配置、生产可用默认密钥 | 拆分 application-dev/prod.yml + StartupValidator（prod 缺密钥拒启） | prod 启动被拒并列出 9 项缺失 |
| P0-3 | 仅本地磁盘存储 | 新增 AliOssStorageService（条件装配，local 为默认） | dev 回归通过（OSS 待真实凭证） |
| P0-4 | 自研 SHA-256 密码哈希强度不足 | PasswordEncoder 升级 BCrypt（预哈希+pepper），兼容旧格式并**登录时自动升级** | 三项测试：新密码 $2a$ / 账密登录 / 旧哈希自动迁移 |
| P0-5 | 内容安全为占位实现 | 接入微信 msgSecCheck + mediaCheckAsync（含 fail-open 降级）+ WxAccessTokenService | dev 跳过、回归正常（待真实凭证验证） |
| P0-6 | 无用户协议/隐私政策页 | 新增两页面 + 登录页与「我的」页入口 | JS 语法 + app.json 校验通过 |

附带：`start-dev.sh` 增加端口/依赖/env 三项前置检查；新增 `docs/RUNBOOK.md` 启动手册；`.env.example` 补全生产必需项。

## 三、发现的问题

### 安全类
1. **P0 漏洞**：`GET /api/admin/users` 实测返回 `openid` 与 `passwordHash`（用户 wangmin/yw978712 哈希可见）——根因是 Controller 直接返回实体
2. **内容安全缺口**：发布任务时标题/描述**从未调用检测**（AGENTS §9 有要求），本次补齐
3. **日志泄露**：`WxAuthService` 打印完整 openid；微信失败响应整体打日志（可能含 session_key）
4. 密码哈希为自研 SHA-256（无慢哈希，GPU 暴力破解成本低）

### 架构/工程类
5. 模块耦合：task 模块被跨模块 import **64 处**（直接引用 Mapper/Entity）
6. 事务边界不完整：全项目仅 **7 处** `@Transactional`
7. Entity 直接出 Controller：3 处（本次修复 3 个）
8. **零测试代码**（`src/test` 为空）
9. API 文档无注解（Swagger 标题为 `list`/`detail` 等方法名）
10. 前端 3 套登录相关页（login 已删 / login-preview / login-v2）
11. 工作区含无关文件 `build_notebooks.py`（numpy 教程脚本）

### 运行期踩坑（已修复）
12. `app_config` 种子值 `content.security.enabled=true` 导致 **dev 也调用微信** → 500；改为开关读 yml（环境决定）
13. `BusinessException` 被笼统当作"违规拦截"上抛 → 500；改为仅 `CONTENT_ILLEGAL` 上抛
14. 端口 8080 被已有实例占用 → 新实例启动失败（现象像"后端挂了"）；启动脚本已加检测
15. `pkill -f "spring-boot:run"` 会匹配到自身命令行（操作坑）；改用 `[s]pring-...` 且不与启动同命令执行

## 四、当前状态

- 提交：`1284c29`（基线）→ `3d7b345`（P0 代码）→ `b1f5ba2`（文档）
- 规模：后端 104 个 Java 文件 / 32 个接口；小程序 16 个页面
- 服务：MySQL 3306、Redis 6379 运行中；8080 由用户自行启动

## 五、遗留问题（下一步）

### P1 标准化（对标苍穹外卖）
- [ ] Maven 三模块拆分（common/pojo/server）
- [ ] 包结构规范化（constant/context/enumeration/exception/json/properties/result/utils）
- [ ] 常量类体系（消除硬编码提示语）
- [ ] API 文档注解 + Knife4j 中文文档
- [ ] 业务缓存（任务列表/详情 Redis）
- [ ] 测试体系（JUnit5 + ArchUnit + Testcontainers）
- [ ] 前端清理（冗余页 + 统一规范）

### P2 增强
- [ ] Druid + SQL 监控 / MapStruct / Redisson 分布式锁 / Micrometer 监控 / CI-CD

### 需外部凭证（代码就绪）
- [ ] OSS 端到端（阿里云 AK + Bucket）
- [ ] 内容安全端到端（微信 AppID/Secret）
- [ ] 真实微信登录（替换 mock）

### 上线硬门槛（非技术为主）
- [ ] **类目资质确认**（任务/兼职类可能需人力资源资质）
- [ ] 微信隐私保护指引配置（与 privacy 页面逐项对齐）
- [ ] HTTPS + 域名备案 + request 合法域名
- [ ] 压测（并发接取）、数据库备份与恢复演练、日志采集策略

---

# 📅 2026-09-26 工作记录：缺陷批修（15 项）+ 独立库冒烟验证

## 一、背景

本轮不做中间件扩展，先做**以代码为证据的缺陷审查**：静态通读后端 104 个 Java 文件 + 4 个 Flyway 迁移 + 8 份文档，
确认 15 项缺陷（安全 / 并发 / 状态机为主），一次性修复并在**独立测试库**上端到端验证。

## 二、修复清单（含 2 个新增文件）

| ID | 问题 | 修复 | 位置 |
|---|---|---|---|
| S1 | `bizType` 无白名单即参与磁盘路径拼接 → **目录逃逸写文件** | Controller 白名单（submission/avatar）+ 本地/OSS 存储二次校验 + `normalize()` 后 `startsWith(baseDir)` | `FileController` / `LocalStorageService` / `AliOssStorageService` |
| S2 | 凭证 `fileIds` 不校验归属与内容安全 → **引用他人文件 / 绕过检测** | 校验存在性（未逻辑删除）+ `uploader_id` + `sec_status != 违规` | `SubmissionService.validateFileIds()` |
| S3 | 原子扣减不判 `claim_deadline` → 截止后 1 分钟窗口仍可接取 | SQL 增加 `AND claim_deadline > NOW() AND deadline > NOW()`；失败分支区分「已过截止」与「名额已满」 | `TaskMapper` / `ClaimService` |
| S4 | `submit`/`review` 不校验任务状态 → 已取消任务仍可提交/审核 | 跨聚合校验任务状态 ∈ {OPEN, IN_PROGRESS, REVIEWING} | `SubmissionService` / `ReviewService` |
| S5 | 定时任务不触发收尾 → 任务卡 IN_PROGRESS、**settlement 永不生成** | `finalizeTaskIfNeeded` 改 public + `@Transactional`；自动通过/超时取消后调用；结算插入幂等 | `ScheduledTasks` / `ReviewService` |
| S6 | 注销只拉黑当前 jti → 其它设备 token 仍可用 | 注销时撤销该用户全部 jti（复用 `user:{id}:jtis` 集合） | `AuthService.revokeAllSessions()` |
| S7 | prod 未关闭 API 文档 | `application-prod.yml` 关闭 SpringDoc api-docs / swagger-ui | `application-prod.yml` |
| S8 | `/files/**` 无鉴权且不在限流范围 | 纳入限流拦截器（scope=fileview）；私有桶 + 签名 URL 留待 OSS 阶段 | `WebConfig` / `RateLimitInterceptor` |
| S9 | 限流与审计的 key 取自可伪造的 `X-Forwarded-For` | 新增 `ClientIpResolver`：默认只信 `remoteAddr`，需显式开启才信 XFF | `ClientIpResolver`（新）/ `RateLimitInterceptor` / `AuditService` |
| C1 | `@Version` **空转**（未注册乐观锁拦截器）→ README「三级防线」名不副实 | 注册 `OptimisticLockerInnerInterceptor`（先乐观锁、分页放最后） | `MybatisPlusConfig` |
| C2 | 覆盖提交重置 48h 审核窗口 → 可反复拖单 | 仅首次提交写 `submitted_at` / `review_deadline` | `SubmissionService` |
| C3 | 定时任务混用应用时钟、且迁移无状态审计 | 时间判定改 DB `NOW()`；逐条 CAS + 写 `task_status_log` | `ScheduledTasks.expireByStatus()` |
| C4 | `GET /api/tasks` 内直接写库（惰性过期） | 移除读路径写库，过期统一由定时任务负责 | `TaskService.list()` |
| C5 | 代码读取的配置键种子缺失 / 种子键无人读 | 新增 `V5__add_missing_config_keys.sql`；`page.size.max` 由 ApplicationRunner 启动后生效 | `V5`（新）/ `MybatisPlusConfig` |
| 附 1 | `settleApproved` 重跑会重复插入结算 | 按 task 已存在结算跳过（幂等） | `ReviewService` |
| 附 2 | 事务内手动回退名额是死代码（误导阅读） | 删除 3 处 `decrementClaimedCount()` 手动回退并注释说明回滚语义 | `ClaimService` |

## 三、验证（独立库，未触碰演示库）

1. `mvn -s settings-mirror.xml clean compile -DskipTests` → **BUILD SUCCESS**（105 源文件，5.3s）
2. 新建独立库 `treatbord_test`（授权 `app_user` / `db_migrate`），以 `MYSQL_DB=treatbord_test SERVER_PORT=18080` 启动
   → Flyway 应用 **V1–V5**，`Started TreatbordApplication in 5.24s`
3. mock 双用户 + curl 断言：

| 用例 | 结果 |
|---|---|
| `POST /api/files?bizType=../../evil` | 400「不支持的 bizType」（uploads 下无逃逸目录） |
| 提交引用他人 fileId | 403「只能引用自己上传的文件」 |
| 提交引用不存在 fileId | 400「存在无效或已删除的 fileId」 |
| 对已过 `claim_deadline` 的任务接取 | 2002「任务已过接取/完成截止时间」 |
| 对 CANCELLED 任务提交凭证 | 3003「任务当前状态（CANCELLED）不可提交凭证」 |
| 覆盖提交前后 `review_deadline` | 完全一致（未重置） |
| `submitted_at` 改 49h 前 → 等定时任务 | claim=APPROVED，task=**SETTLED**，settlement **1 笔 / 1.00**，status_log 三段完整 |
| 过期 OPEN 任务 → 等定时任务 | task=EXPIRED，`task_status_log` 记录 `OPEN->EXPIRED / 接取截止已过自动过期` |
| 注销后复用旧 token | 401「登录已失效，请重新登录」 |
| `app_config` 新键 | `credit.claim.threshold=60`、`credit.penalty.reject=5`、`fileview.rate.limit.per.minute=120`、`page.size.max=20` |

## 四、遗留

- `src/test` 仍为空：本轮验证依赖冒烟脚本，**尚未形成自动化回归**（下一步补 并发防超卖 / IDOR / 状态机 三类测试）
- S8 的完整方案（私有桶 + 签名 URL）依赖 OSS 凭证
- 低危遗留：`unbanUser` 无事务、jti 黑名单 TTL 取全生命周期、限流仅覆盖 6 类 URI、dev 文件 URL 写死 `127.0.0.1`

## 五、副作用与回滚

- 新增数据库 `treatbord_test`（建议保留作集成测试库）；`treatbord` 演示库未被触碰
- 冒烟产生的上传文件已清理；18080 测试实例已停止
- 本次改动集中在 15 个文件 + 2 个新增文件，未提交前可用 `git checkout -- <file>` 回滚

---

# 📅 2026-09-27 工作记录：Spring 事务收口（旁路操作外移 + 回滚规则统一）

## 一、背景

承接 09-26 的缺陷批修，本轮把 **Spring / 事务** 这一块做完整：统一"事务内不做旁路操作"的模式，并补齐管理员接口缺失的事务保护。

## 二、改动清单（5 文件修改 + 1 新增，+48 / −34）

| 文件 | 变更 | 内容 |
|---|---|---|
| `common/AfterCommit.java`（新增，50 行） | 新 | 事务提交后回调工具：有事务则注册 `afterCommit`，无事务则直接执行，回调异常只记日志 |
| `task/service/ClaimService.java` | +12/−8 | `claim()` / `cancelClaim()` 的审计移到提交后；2 处 `rollbackFor` |
| `task/service/TaskService.java` | +2/−2 | `cancel()` 加 `rollbackFor` |
| `submission/service/SubmissionService.java` | +25/−14 | `submit()` 的审计 + 发布者通知移到提交后；加 `rollbackFor` |
| `review/service/ReviewService.java` | +35/−18 | `review()` 的通知+审计、收尾的 EXPIRED 通知、结算的 SETTLE 审计全部移到提交后 |
| `admin/service/AdminService.java` | +8/−4 | `handleReport()` / `unbanUser()` 补事务；4 处注解统一 |

**判断标准（写进代码注释）**：影响业务一致性的（状态留痕 `claim_status_log`、结算 `settlement`）**留在事务内**；旁路可失败的（通知、审计 `audit_log`）**移到提交后**。

**统一规则**：全项目 **10 处 `@Transactional`** 全部改为 `@Transactional(rollbackFor = Exception.class)`（默认只回滚 `RuntimeException`/`Error`，受检异常会静默提交）。

## 三、验证（独立库 `treatbord_test` + 18080 实例，未触碰演示库）

| 流程 | 断言 | 结果 |
|---|---|---|
| 编译 | `mvn -s settings-mirror.xml clean compile` | BUILD SUCCESS（106 文件） |
| A 接取→提交→通过 | task=`SETTLED`、settlement 1 笔；审计 `CLAIM_TASK`/`SUBMIT`/`REVIEW_APPROVED`/`SETTLE` **各 1**、通知 1 | ✅ |
| B 提交→驳回 | claim=`REJECTED`、信用分 **100→95**、通知 1、`REVIEW_REJECTED`=1 | ✅ |
| C 接取超时（改 create_time 为 73h 前） | claim=`CANCELLED` → task=`EXPIRED` → 发布者通知 **+1**，日志 `IN_PROGRESS->EXPIRED/无凭证审核通过` | ✅ |
| D 取消接取 | claim=`CANCELLED`、`claimed_count` 1→0、`CANCEL_CLAIM` 审计=1 | ✅ |

**顺序证据**：审核请求的 SQL 序列中，通知与 REVIEW 审计是该请求的**最后两条**语句（事务内写入之后），符合"提交后执行"。

## 四、当前技术栈版本（实测解析，非文档口径）

| 组件 | 版本 | 说明 |
|---|---|---|
| Spring Boot | **3.5.16** | `spring-boot-starter-parent` 统一管理 |
| Java | 21（JDK 21.0.12.1） | |
| Spring Framework | 6.2.19 | spring-tx / spring-aop 等 |
| MyBatis-Plus | 3.5.12 | starter + jsqlparser |
| MySQL Connector/J | 9.7.0 | runtime |
| Flyway | 11.7.2 | core + mysql |
| spring-security-crypto | 6.5.11 | 仅用 BCrypt |
| jjwt | 0.12.6 | JWT |
| springdoc-openapi | 2.8.9 | Swagger UI |
| aliyun-sdk-oss | 3.18.5 | 对象存储 |
| Micrometer | 1.15.12 | actuator 传递依赖，未接 Prometheus |
| Maven | 3.9.12 | WSL |
| 运行环境 | MySQL 8.4 / Redis 8 | WSL 内 |

> 注：`org.aspectj:aspectjweaver` **不在 classpath**，因此项目里写 `@Aspect` 不会生效；要引入声明式切面需先加 `spring-boot-starter-aop`。

## 五、遗留

- **长事务**：`SubmissionService.submit()` 中 `contentSecurityService.checkText()` 是网络调用，仍在事务内持有连接 → 待拆为"非事务编排 + 事务内 DB 方法"（新 Bean 或 `TransactionTemplate`）
- **文档修正**：README 技术栈表中 "Flyway V1~V4" 应为 **V1~V5**
---

# 📅 2026-09-27 · 升级批次 B1：测试体系与 CI

## 一、背景

升级路线 W1 第一项：项目此前 **0 个测试、无 CI**，所有修复只靠人工冒烟验证，无法防回归。本批建立测试体系。

## 二、新增内容

| 类型 | 文件 | 用例数 | 覆盖 |
|---|---|---|---|
| 纯单测 | `TaskStatusTest` / `ClaimStatusTest` | 36 | 状态机合法流转、非法流转一律 409、终态不可再流转、`of()` 解析 |
| 纯单测 | `PasswordEncoderTest` | 5 | BCrypt 格式、加盐唯一、旧 SHA-256 可校验且标记升级、pepper 隔离、空值/非法哈希 |
| 纯单测 | `JwtUtilTest` | 7 | 签发解析回读、jti 唯一、篡改/换密钥/换签发者/过期均无效 |
| 纯单测 | `FileValidationServiceTest` | 6 | 真实 PNG/JPG/WEBP 通过、伪装扩展名 4001、非白名单 4001、超限 4002、空文件 400、头截断拒绝 |
| 集成测试 | `ClaimConcurrencyTest` | 2 | **20 线程抢 5 名额防超卖**、同一用户并发重复接取只成功一次 |
| 集成测试 | `IdorAuthorizationTest` | 4 | 取消他人接取 403、非发布者审核 403、非当事人查看详情 403、非发布者取消任务/查列表 403 |
| 集成测试 | `StateMachineIntegrationTest` | 5 | 取消后提交 3003、通过后复审 3004、取消后接取 2002、自接自单 2005、通过后驳回 3004 |
| 测试基类 | `support/AbstractIntegrationTest` | — | `@SpringBootTest` + `@ActiveProfiles("test")`，造数 + **物理清理测试数据** |
| 测试配置 | `src/test/resources/application-test.yml` | — | 指向 `treatbord_test` 独立库（环境变量注入账号密码），不触碰演示库 |
| CI | `.github/workflows/ci.yml` | — | MySQL 8.4 + Redis 8 service 容器 + JDK 21 + `mvn -B verify` |

## 三、验证结果

```
mvn -s settings-mirror.xml test
→ Tests run: 65, Failures: 0, Errors: 0, Skipped: 0
→ BUILD SUCCESS
[并发验收] 线程=20 名额=5 成功=5 名额已满=15 claimed_count=5
```

| 断言 | 结果 |
|---|---|
| 成功接取数 = 名额（5） | ✅ 零超卖 |
| 其余 15 次均以「名额已满」失败 | ✅ 无其他异常类型 |
| `claimed_count` = 5 = `task_claim` 行数 | ✅ 计数与实际一致 |

**运行方式（本地）**：
```bash
cd ~/Project/treatbord
set -a && source .env.local && set +a
export MYSQL_DB=treatbord_test
mvn -s settings-mirror.xml test
```

## 四、遗留

- CI 首次运行需在 GitHub Actions 上验证（本地无法执行 workflow）
- B2 待办：定时任务分批处理、核心 SQL 的 `EXPLAIN` 复核
---

# 📅 2026-09-27 · 升级批次 B2：定时任务分批处理 + 扫描索引

## 一、背景

定时任务此前用 `selectList` 把**全量**待处理记录读进内存（10 万行任务即堆压力）；且三个扫描按时间列过滤，但时间列不在索引里（`type=ref` + `Using where`，要扫完整个 status 区间）。

## 二、改动

| 文件 | 改动 |
|---|---|
| `ScheduledTasks.java` | 四个方法全量加载 → **分批循环**（`BATCH_SIZE=500`，`MAX_BATCHES_PER_RUN=200` → 单轮上限 10 万行） |
| 同上 | `expireByStatus` / `cancelOverdueClaims` / `autoApprove`：**处理过的记录状态即变**，下一批查询自然不重复（无需 offset） |
| 同上 | `reconcile`：只读任务，改为**主键游标**（`id > lastId` + `ORDER BY id`），否则会反复扫同一批 |
| 同上 | 三个扫描补时间列 `ORDER BY`：分批顺序确定（先处理最久远的）+ 查询能走 `(status, 时间列)` 索引 |
| 同上 | 逐条 `log.info` → `log.debug`，每轮改为**一条汇总日志**（否则 10 万行刷爆日志） |
| `V6__add_claim_scan_indexes.sql` | 新增 `idx_claim_status_ctime(status, create_time)`、`idx_claim_status_submitted(status, submitted_at)` |

## 三、验证（treatbord_test 实例实跑）

**数据规模**：2 万条过期 OPEN 任务 + 1200 条超时 CLAIMED 接取 + 1200 条超时 SUBMITTED 凭证

**处理结果（一轮调度全部处理完）**：

```
[SCHED] expireTasks: OPEN→EXPIRED=20000, IN_PROGRESS→EXPIRED=0
[SCHED] cancelOverdueClaims: 本轮共取消 1200 条超时接取
[SCHED] autoApprove: 本轮自动通过 1200 条
```

**分批证据**（SQL 日志采样，共 50 处 `LIMIT 500`）：

```sql
SELECT ... FROM task_claim WHERE deleted=0 AND (status = ? AND submitted_at < ?) ORDER BY submitted_at ASC LIMIT 500
```

**`EXPLAIN` 复核（V6 前后对比）**：

| SQL | V6 之前 | V6 之后 |
|---|---|---|
| A 列表分页（status + create_time 排序） | `ref` + `idx_task_status_ctime` + Backward index scan | 不变 ✅（本就没有 filesort） |
| C 任务过期扫描 | `ref` + `Using where`（扫 status 全区间） | **`range` + `Using index condition`**（`idx_task_status_claimdead`） |
| D 接取超时扫描 | `ref` + `Using where` | **`range` + ICP**（新索引 `idx_claim_status_ctime`） |
| E 审核超时扫描 | `ref` + `Using where` | **`range` + ICP**（新索引 `idx_claim_status_submitted`） |

**回归**：`mvn test` → `Tests run: 65, Failures: 0, Errors: 0` · BUILD SUCCESS

## 四、取舍

- **索引代价**：`task_claim` 增加两个二级索引，写入量低（接取/提交时才写），可接受
- **单轮上限**：200 批 × 500 = 10 万行；超出部分留到下一分钟继续（避免单次调度长时间占用调度线程）
- **日志**：逐条改 debug，生产 `com.treatbord: info` 下只留汇总，避免海量日志
- 本次数据全部在 `treatbord_test`，验证后已清理（残留 0 行）
---

# 📅 2026-09-27 · 升级批次 B3：长事务治理（内容安全检测移出事务）

## 一、问题

`SubmissionService.submit()` 原本是一个大 `@Transactional` 方法，里面包含：

```
归属校验 → 状态校验 → 任务状态校验 → 文件校验
→ 【内容安全检测】← 真实环境是 HTTP 调用（msgSecCheck），耗时 100~500ms
→ 写 submission → CAS 流转 → 审核窗口 → 状态留痕
```

事务在**等待外部 HTTP 响应期间**一直占用数据库连接与行锁（Hikari 池仅 10 个连接），并发提交时会放大成"连接被占满"。

## 二、改动

| 文件 | 改动 |
|---|---|
| `submission/service/SubmissionTxService.java`（新增，150 行） | 承载**事务内写入**：权威校验 → 覆盖/新建 submission → CAS 流转 → 审核窗口 → 状态留痕 → `AfterCommit` 提交后审计与通知 |
| `submission/service/SubmissionService.java` | `submit()` 去掉 `@Transactional`，改为**非事务编排**：① 事务外预校验 ② 内容安全检测（事务外）③ 调 `SubmissionTxService.persistSubmission()` |
| `src/test/.../SubmissionTransactionBoundaryTest.java`（新增） | 断言**内容安全检测时不在事务中**，且写入在事务内完成 |

**为什么"事务外预校验 + 事务内再校验"两遍**：
- 事务外预校验 → **快速失败**，避免为明显无效的请求去调外部内容安全接口（省时省钱）
- 事务内重读再校验 → 关闭"预校验之后状态被并发修改"的窗口；状态流转仍有 CAS 兜底

## 三、验证

```
mvn -s settings-mirror.xml test
→ Tests run: 66, Failures: 0, Errors: 0, Skipped: 0
→ BUILD SUCCESS
```

新增断言（`SubmissionTransactionBoundaryTest`）：

```java
doAnswer(inv -> {
    checkRanInsideTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
    return null;
}).when(contentSecurityService).checkText(any(), any(), any());

submissionService.submit(claimId, claimerId, req, null);

assertFalse(checkRanInsideTransaction.get(), "内容安全检测必须在事务之外执行，否则就是长事务");
assertEquals(ClaimStatus.SUBMITTED.name(), taskClaimMapper.selectById(claimId).getStatus());
assertNotNull(submissionMapper.selectLatestByClaimId(claimId));
```

**要点**：这个断言直接测量"外部调用发生时的线程事务状态"，比看日志更硬——**验证的是设计目标本身**，而不是间接现象。

## 四、取舍

- **多了一个 Bean**：`SubmissionService`（编排）与 `SubmissionTxService`（事务）职责分离，符合"事务边界只包住数据库操作"的原则
- **多一次校验查询**：事务内重读 claim/task，代价可忽略（两条主键查询），换来窗口关闭
- `@Transactional` 放在**独立 Bean** 上，避免"同类内部调用绕过代理"的经典失效
---

# 📅 2026-09-27 · 升级批次 B4：任务缓存 + 限流 Lua 原子化与覆盖面

## 一、4-1 限流覆盖面

| 改动 | 内容 |
|---|---|
| `RateLimitInterceptor.resolveScope()` | 新增 `taskcreate`（POST /api/tasks）、`report`、`review`、`notify` 四个场景；**按 HTTP 方法区分**——GET `/api/tasks` 是公开浏览（不限流），POST 才是发布（限流） |
| `V7__add_ratelimit_config_keys.sql` | 补齐阈值：`taskcreate=10`、`report=5`、`review=20`、`notify=60`（每分钟，`app_config` 可热调） |

## 二、4-2 任务缓存（新增 `TaskCacheService`，Cache-Aside）

| 设计点 | 做法 | 解决什么 |
|---|---|---|
| 列表缓存 key | `cache:task:list:v{版本}:{status}:{keyword}:{page}:{size}` | key 带版本号 → 写操作只 `INCR cache:task:listver` 即可整体失效，**避免 SCAN 批量删除** |
| 详情缓存 | `cache:task:detail:{id}`，只缓存**共享字段**（含发布者昵称） | `claimedByMe` 依赖登录用户 → 命中后单独补算，不污染缓存 |
| **防穿透** | 查不到的任务写"空值标记"，TTL **60s** | 恶意刷不存在的 id 不会反复打库 |
| **防雪崩** | TTL = `task.cache.ttl.seconds`（默认 300）+ **0~60s 随机抖动** | 避免大批 key 同时失效 |
| **失效时机** | 接入 7 处写路径：创建/取消任务、接取/取消接取、提交凭证、审核与收尾、管理端下架、定时过期扫描 | 读到的数据与库一致（定时批量扫描走"列表整体失效"） |
| 降级 | Redis 异常 = 未命中/跳过写缓存 | 缓存故障不影响主流程 |

## 三、4-3 限流 Lua 原子化

```lua
local c = redis.call('INCR', KEYS[1])
if tonumber(c) == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
return c
```

- 原实现 `INCR` + `EXPIRE` 是**两次网络往返**：若两步之间异常，key 会**没有 TTL**（脏 key 永久驻留）
- 改为 Lua 后"计数 + 首次设置 TTL"在 Redis 内部**原子执行**，不存在中间态
- 固定窗口算法语义**不变**（滑动窗口 ZSet 方案留待后续）

## 四、验证

**单测/集成测试**：`mvn test` → `Tests run: 72, Failures: 0, Errors: 0` · BUILD SUCCESS

新增 6 例：

| 用例 | 断言 |
|---|---|
| `TaskCacheTest.listCacheHitThenInvalidated` | 直接改库后列表仍返回旧值（命中缓存）→ 接取后返回新值（失效生效） |
| `TaskCacheTest.detailCacheHitThenInvalidated` | 同上；且 `claimedByMe` 对接取者为 true、对他人为 false（每用户字段不缓存） |
| `TaskCacheTest.absentTaskIsNegativelyCached` | 不存在的任务第二次仍返回 2001（空值缓存防穿透） |
| `RateLimitServiceTest.luaScriptSetsTtlAtomically` | 阈值 2 → 第 3 次拒绝；**key 的 TTL > 0**（Lua 原子设置） |
| `RateLimitServiceTest.defaultLimitApplies` | 未配置时用默认 60，不误伤 |
| `RateLimitCoverageTest.reportEndpointIsRateLimited` | 阈值 1 时 `POST /api/reports` 第 2 次返回 **429** |

**运行时冒烟（treatbord_test + 18080）**：

```
[CACHE] 列表回填 key=cache:task:list:v24:_:_:1:5 ttl=330s
[CACHE] 列表命中 key=cache:task:list:v24:_:_:1:5
[CACHE] 详情回填 taskId=1 ttl=315s
[CACHE] 详情命中 taskId=1

ratelimit:report:127.0.0.1:29841936  TTL=45  value=6      ← TTL 由 Lua 原子设置
POST /api/reports 连打 6 次：前 5 次 code=0，第 6 次 code=429
```

## 五、取舍

- **缓存一致性**：定时任务批量过期只做"列表整体失效"，详情缓存靠 TTL（≤300s）兜底 —— 对"过期任务详情仍显示 OPEN"的窗口可接受（不影响接取，接取仍受原子 SQL 的时间条件保护）
- **多一次 Redis 往返**：列表/详情读多一次 `GET`，但换来数据库压力下降；缓存故障自动降级为直查
- **版本号方案**：旧版本 key 不删除、随 TTL 自然过期，避免了 `SCAN`+`DEL` 的复杂度与风险
---

# 📅 2026-09-27 · 升级批次 B5：文件签名 URL + 审计查询 + 登录失败锁定

## 一、背景

`/files/**` 是**无鉴权直读**（小程序 `<image src>` 带不了 Authorization 头），防护仅靠"UUID 不可猜"：URL 一旦泄露即**永久可访问**、无法撤销。同时发现一个**真实缺陷**：`SubmissionVO.fileUrls` **从未被后端填充** → 审核页的图片网格一直是空的。

## 二、B5a：签名基础设施 + 风控

| 项 | 实现 |
|---|---|
| **签名算法** | `sig = Base64Url(HMAC-SHA256(secret, storageKey + ":" + exp))`；校验"未过期 + `MessageDigest.isEqual` 常量时间比较" |
| **强制开关** | `treatbord.storage.signed-url.required`：**dev 默认 false**（便于联调）/ **prod 强制 true**；缺签名/伪造/过期 → **403** |
| **密钥治理** | `STORAGE_SIGN_SECRET` 纳入 StartupValidator 生产必填项，且不得为开发默认值 |
| **URL 动态化** | 签名 URL 与上传响应按**当前请求 Host** 拼装 → 解决 dev 下写死 `127.0.0.1:8080` 的问题（B5a-2） |
| **路径归属** | `FileViewController` 补 `normalize()` 后 `startsWith(baseDir)` 校验 |
| **审计查询** | 新增 `GET /api/admin/audit-logs?action=&userId=&page=&pageSize=`，返回 `AuditLogVO`（不暴露实体），`@RequireAdmin` 保护 |
| **登录失败锁定** | `LoginAttemptService`：**IP 维度硬锁**（`login.fail.threshold`/`login.fail.lock.minutes` 可热调）+ **账号维度只累计不硬锁**（否则"知道用户名就能锁死他人账号"）；登录成功清零；V8 补配置键 |

## 三、B5b：读时签名（授权 = 上传者 + 接取双方）

```java
// SubmissionViewAssembler：组装凭证 VO 时下发签名 URL
public SubmissionVO assemble(TaskSubmission sub) {
    SubmissionVO vo = SubmissionVO.from(sub);
    if (vo != null && sub.getFileIds() != null) {
        List<Long> ids = parseFileIds(sub.getFileIds());
        vo.setFileIds(ids);
        vo.setFileUrls(fileService.signedUrls(ids));   // 签名（授权由调用方业务校验负责）
    }
    return vo;
}
```

**授权原则**：**不按"角色"授权，而是对齐业务可见性**——能查看这条凭证的人（接取者本人 / 任务发布者），就能拿到其中图片的签名 URL。因为复用了已有的归属校验，**规则只有一套**，不会出现"能看凭证却看不到图"或反过来的割裂。

**顺带修复**：审核页图片网格空白（fileUrls 从未填充）—— 现在 `detail()` 与发布者的接取列表都会下发签名 URL。

**一个架构细节**：若让 `TaskService` 直接依赖 `SubmissionService`，会出现 `TaskService → SubmissionService → ClaimService → TaskService` **循环依赖**（Spring Boot 2.6+ 默认禁止）。故抽出独立的 `SubmissionViewAssembler`（只依赖 FileService）打破环。

## 四、验证

**测试**：`mvn test` → `Tests run: 87, Failures: 0, Errors: 0` · BUILD SUCCESS（新增 15 例）

| 用例 | 断言 |
|---|---|
| `FileUrlSignerTest`（6） | 签名可校验；**篡改路径失效**；伪造/缺参/非法时间戳拒绝；过期拒绝；**换密钥全局失效**；required 开关 |
| `FileSignedUrlAccessTest`（5） | `required=true` 下：无签名 403、正确签名 200、伪造 403、过期 403、**拿 A 文件的签名访问 B 文件 403** |
| `SubmissionFileUrlTest`（1） | 上传响应带 `exp/sig`；接取者与发布者都能拿到签名 URL；**陌生人连详情都 403**；发布者列表同样带签名 URL |
| `LoginLockoutTest`（2） | 连续 5 次失败后**正确密码也 429**；未达阈值正常登录且成功后计数清零 |
| `AdminAuditLogTest`（1） | 普通用户 403（`@RequireAdmin`）；管理员可查到记录 |

**运行时开关验证**（`STORAGE_SIGNED_URL_REQUIRED=true`，模拟生产）：

```
无签名访问 /files/...        → HTTP 403
伪造签名访问                → HTTP 403
openssl 独立计算合法签名访问 → HTTP 200   ← 交叉验证 HMAC 实现一致
```

## 五、取舍

- **签名 ≠ 加密**：不隐藏内容，只证明"服务端签发 + 未过期 + 未篡改"；短 TTL（300s）控制泄露影响，换密钥可全局失效
- **账号维度不硬锁**：避免被用来恶意锁定他人账号（用账号维度做硬锁是常见的"DoS 自家用户"设计错误）
- **读时签名 vs 独立签名接口**：选读时签名，授权天然复用业务校验；代价是响应不能被缓存，TTL 需覆盖用户看图时长
- **定时任务下的详情缓存**：批量过期只让列表缓存失效，详情靠 TTL 兜底（≤300s），不影响接取正确性（原子 SQL 仍带时间条件）

### 补充（同日调整）：签名 TTL 300s → 1800s

原 5 分钟有效期在实际使用中偏紧（审核页停留超过 5 分钟，图片会 403）。调整为 **30 分钟**：

| 项 | 调整前 | 调整后 |
|---|---|---|
| `treatbord.storage.signed-url.ttl-seconds`（dev/prod/test） | 300 | **1800** |
| `FileUrlSigner` 代码默认值 | 300 | **1800** |

**为什么 30 分钟够用**：签名 URL 是**无状态凭证**，服务端不记录"谁在哪个页面"，所以无法精确到"退出界面即失效"；能用的手段只有——**短 TTL、换密钥、绑定会话**。30 分钟覆盖了正常浏览/审核时长，同时泄露窗口远小于登录会话（JWT 7 天）；若将来需要"登出即失效"，可把**会话版本**写进签名（校验时查一次 Redis），用有状态换精确性。

**前端兜底（待 B7）**：`<image binderror>` 触发时重新拉一次详情拿新签名 URL 重试一次。

---

# 📅 2026-09-27 · 升级批次 B6：可观测（指标 + 慢接口/慢 SQL 日志）

## 一、改动

| 项 | 实现 |
|---|---|
| **指标采集** | pom 增加 `micrometer-registry-prometheus`；dev/test/prod 暴露 `/actuator/prometheus` |
| **业务指标**（`BusinessMetrics`） | 接取结果 / 限流拒绝 / 审核决策与等待时长 / 定时任务执行结果与处理条数 |
| **慢接口日志**（`SlowRequestLoggingFilter`） | 用 **Filter** 而非拦截器，覆盖 `/files/**`、`/actuator/**`；阈值 `treatbord.observability.slow-api-ms` |
| **慢 SQL 日志**（`SlowSqlInterceptor`） | MyBatis 插件，挂 **StatementHandler.query/update/batch**；阈值 `slow-sql-ms`；只记 MappedStatement id，**不打印 SQL 与参数** |
| **签名失败日志** | `FileViewController` 403 前打 WARN（key/exp/hasSig），**不打印 sig**；用于区分"签名失效"与"请求未到服务端" |
| 阈值配置 | dev 500/300ms · prod 800/500ms · test 500/300ms |

## 二、指标清单（实测输出）

```
treatbord_claim_result_total{result="success"} 1.0
treatbord_claim_result_total{result="full"}    1.0
treatbord_ratelimit_rejected_total{scope="report"} 1.0
treatbord_schedule_runs_total{result="success",task="expireTasks"} 1.0
treatbord_schedule_runs_total{result="success",task="cancelOverdueClaims"} 1.0
treatbord_schedule_runs_total{result="success",task="autoApprove"} 1.0
```
标准指标（`jvm_memory_used_bytes`、`http_server_requests_seconds_count`）同时正常采集。

**标签基数控制**：接取结果把错误码映射为固定低基数标签（`full/not_claimable/duplicate/self_claim/credit_not_enough/other`），避免 `result` 维度爆炸。

## 三、踩坑记录：MyBatis 扩展点选择（值得记住）

**现象**：`SlowSqlInterceptor` 挂 `Executor.query(MappedStatement, Object, RowBounds, ResultHandler)` 与 `Executor.update` 时，
插件**确实在链上**（`sqlSessionFactory.getConfiguration().getInterceptors()` 能看到它，位于 `MybatisPlusInterceptor` 内层），
但 `intercept()` **从未被调用**（加 `System.out` 探针验证：无输出）。

**结论**：在本项目链路（MyBatis 3.5 + MP 3.5.12 + `SqlSessionTemplate`）下，`Executor` 的 4 参 `query` 签名没有实际经过代理；
**改挂 `StatementHandler.query/update/batch` 后立即生效**，而且它更贴近"慢 SQL"语义（真正执行 JDBC 的地方）。

**学习点**：MyBatis 四大扩展点（Executor / StatementHandler / ParameterHandler / ResultSetHandler）各有适用场景；
排查插件是否生效要**用探针验证"是否被调用"**，而不是只看"是否在链上"。

## 四、日志脱敏复核（6-4 结论）

| 检查项 | 结果 |
|---|---|
| 是否记录 password / token / secret / pepper | ✅ 无 |
| openid 是否脱敏 | ✅ `WxAuthService` 用 `MaskUtil.maskOpenid()`（唯一一处 openid 日志，且在 `[WX-MOCK]` 分支，生产走真实微信不经过） |
| 签名 URL / sig 是否进日志 | ✅ 只记 `storageKey`、`exp`、`hasSig` 布尔值，**不记 sig** |
| 慢 SQL 日志是否泄露数据 | ✅ 只记 MappedStatement id，不带 SQL 文本与参数 |

## 五、验证

- `mvn test` → **Tests run: 93, Failures: 0, Errors: 0**（新增 6 例：业务指标 4 + 慢日志 2）
  * `BusinessMetricsTest`：接取 success/full 双标签、限流 rejected、审核决策+等待时长、定时任务 runs
  * `ObservabilityLoggingTest`：阈值覆盖为 0 时确实写出 `[SLOW-SQL]` / `[SLOW-API]`（用 logback `ListAppender` 捕获）
- 运行时：`/actuator/prometheus` 实测输出见上；冒烟数据已清理（残留 0）