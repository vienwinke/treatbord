# 上线清单（Go-Live Checklist）

> 配套：[RUNBOOK.md](RUNBOOK.md)（启动/运维）· [SECURITY_REVIEW.md](SECURITY_REVIEW.md)（安全审核）· [CONSISTENCY_CHECKLIST.md](CONSISTENCY_CHECKLIST.md)（文档↔代码）

## 一、运行时必需文件（仓库内）

| 路径 | 说明 | 是否必须 |
|---|---|---|
| `pom.xml` | Maven 构建（Spring Boot 3.5.16 / Java 21） | ✅ |
| `src/main/java/**` | 后端源码（115 文件） | ✅ |
| `src/main/resources/application.yml` + `application-prod.yml` | 主配置 + 生产配置 | ✅ |
| `src/main/resources/db/migration/V1~V8` | Flyway 迁移（**启动自动执行，DDL 唯一权威来源**） | ✅ |
| `miniprogram/**` | 小程序（在微信开发者工具上传发布，不部署到后端服务器） | ✅ |
| `.env.example` | 环境变量模板（真实值另存 `.env.local`，不入仓） | ✅ 参考 |
| `LICENSE` | 许可 | 建议 |
| `README.md` / `AGENTS.md` / `docs/**` | 门面 / AI 约定 / 设计文档 | 非运行必需，建议留仓 |
| `.github/workflows/ci.yml` | CI（质量门禁，非运行时） | 建议 |
| `start-dev.sh` | 开发启动脚本（生产用 systemd/容器替代） | 仅开发 |

## 二、必须外部注入的环境变量（**禁止入仓**）

| 变量 | 生产必需 | 说明 / 生成方式 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | ✅ | 激活 prod 配置 |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DB` | ✅ | 内网地址 |
| `MYSQL_USER` / `MYSQL_PASSWORD` | ✅ | **应用账号，最小权限（仅 DML）** |
| `MYSQL_MIGRATE_USER` / `MYSQL_MIGRATE_PASSWORD` | ✅ | Flyway DDL 专用账号 |
| `REDIS_HOST` / `REDIS_PORT` | ✅ | `REDIS_PASSWORD` 建议启用 |
| `JWT_SECRET` | ✅ | **≥32 字节**：`openssl rand -base64 48` |
| `PASSWORD_PEPPER` | ✅ | 禁止开发默认值 |
| `WX_APPID` / `WX_SECRET` | ✅ | 微信公众平台 |
| `STORAGE_SIGN_SECRET` | ✅ | 文件签名 URL 密钥（缺失 → **启动即拒绝**） |
| `WX_LOGIN_ENABLED=true` / `CONTENT_CHECK_ENABLED=true` | ✅ | 生产强制真实校验 |
| `STORAGE_TYPE=oss` + `OSS_ENDPOINT` / `OSS_BUCKET` / `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | 用 OSS 时必需 | 私有桶 |
| `STORAGE_BASE_URL` | 建议 | `https://<备案域名>/files`（签名 URL 前缀） |
| `SERVER_PORT` | 可选 | 默认 8080 |

> 生产启动前由 `StartupValidator` 校验 11 项必需变量与非默认密钥强度，**缺项直接拒绝启动**。

## 三、部署产物（已补齐，位于 `deploy/`）

| 产物 | 位置 | 作用 |
|---|---|---|
| Dockerfile（多阶段） | `deploy/Dockerfile` | maven 构建 → JRE 21 运行；**非 root** + HEALTHCHECK + 时区 |
| 编排 | `deploy/docker-compose.yml` | nginx + app + mysql + redis（`--profile monitoring` 启用 prometheus）；app **不发布端口** |
| Nginx | `deploy/nginx/treatbord.conf.template` | HTTPS 终结、反代、**指标/健康端点限内网**、`/files` 走应用保留签名校验 |
| 备份 | `deploy/scripts/backup.sh` | 容器内 `mysqldump` → gzip → 校验表数 → 保留 N 天 |
| 告警 | `deploy/prometheus/{prometheus,alerts}.yml` | 抓取 + 9 条规则（不可达/5xx/慢接口/限流激增/定时失败/堆内存/连接池排队） |
| 环境变量模板 | `deploy/.env.example` | 生产变量（含 `TRUSTED_PROXIES`） |
| 部署说明 | `deploy/README.md` | 首次部署 / 验证 / 运维 / 注意事项 |

> ⚠️ **反向代理后必须设置 `TRUSTED_PROXIES`**（默认 Docker 网段 `172.16.0.0/12`）：
> 应用只采信来自可信网段的 `X-Forwarded-For`，并取"从右往左第一个不可信地址"，
> 否则限流会把所有用户当成同一 IP（这是本轮一并修掉的真实风险）。

## 四、微信侧（平台配置，不在仓库）

| 项 | 状态 |
|---|---|
| 小程序 AppID（`project.config.json` 内为 `wx0904d791648802d1`） | ✅ 已有 |
| AppSecret（`WX_SECRET`） | ⬜ 待注入 |
| request 合法域名（HTTPS + 域名备案） | ⬜ 待办 |
| 服务器域名 / 业务域名 | ⬜ 待办 |
| **类目资质**（任务/兼职类可能需人力资源资质） | ⬜ **待确认（上架硬门槛，周期最长）** |
| 隐私保护指引 + 用户协议 + 隐私政策 | 🟡 页面已就绪（`agreement` / `privacy`），需在后台配置 |

## 五、上线前检查项

| 项 | 状态 | 证据 |
|---|---|---|
| 自动化测试 | ✅ | 93/93（离线全绿）+ CI **9/9 绿** |
| 并发零超卖 | ✅ | 200 并发抢 5 → 5 成功 / 195 个 `2003`；库侧 `claimed_count=5` |
| 备份恢复 | ✅ | 删表到 0 → 还原 **1.06s**，15/15 表一致 |
| 生产配置安全 | ✅ | springdoc 关闭 · 签名强制 · 密钥全走环境变量 · 11 项启动校验 |
| 密钥不入仓 | ✅ | 全仓扫描 0 命中 |
| 文档一致性 | ✅ | 33 接口 / 115 文件 / 15 页面 / V1~V8 |
| **部署产物** | ✅ | CI `deploy` 作业：`compose config` + `nginx -t` + **真实构建镜像** |

## 六、绝对不能入仓（已由 .gitignore 拦截）

`.env.local` · `STORAGE_SIGN_SECRET` · `JWT_SECRET` · `PASSWORD_PEPPER` · `WX_SECRET` · OSS AK/SK · 数据库密码 · 证书私钥 · `miniprogram/project.private.config.json`
