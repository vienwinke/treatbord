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
