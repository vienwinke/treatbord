# Treatbord 启动手册（RUNBOOK）

> 面向本地开发环境的启动/停止/排障指南。生产部署见 `docs/STANDARDIZATION_PLAN.md` §5。

---

## 一、环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 21 | `java -version` 验证 |
| Maven | 3.9+ | 使用工作区 `settings-mirror.xml`（阿里云镜像 + 本地 `.m2`）|
| MySQL | 8.x | 端口 3306，库名 `treatbord` |
| Redis | 7.x | 端口 6379（登出黑名单/限流/缓存）|
| 微信开发者工具 | 最新 | 运行小程序前端 |

---

## 二、日常启动（三步）

### 1. 启动依赖服务（MySQL + Redis）

```bash
# 检查是否已在运行
ss -tlnp | grep -E "3306|6379"

# 若未运行（WSL 环境用 root 通道启动）
/mnt/c/Windows/System32/wsl.exe -u root -e bash -c 'systemctl start mysql redis-server'
```

### 2. 启动后端（8080）

```bash
cd ~/Project/treatbord
./start-dev.sh          # 等价于：加载 .env.local + mvn spring-boot:run
```

看到 `Started TreatbordApplication in X seconds` 即成功。

> 首次或改了代码后建议先编译：
> ```bash
> mvn -s settings-mirror.xml clean compile -DskipTests
> ```

### 3. 启动前端（微信开发者工具）

1. 打开**微信开发者工具**（Windows 桌面程序）
2. 导入项目，目录填：
   ```
   \\wsl.localhost\Ubuntu\home\KeYa\Project\treatbord\miniprogram
   ```
3. **必做**：右上角「详情」→「本地设置」→ 勾选 **「不校验合法域名…」**（后端是 http，非 https）
4. 点「编译」→ 左侧模拟器渲染小程序

---

## 三、验证清单

```bash
# 后端健康检查
curl http://127.0.0.1:8080/actuator/health

# 任务列表（匿名可访问）
curl "http://127.0.0.1:8080/api/tasks?page=1&pageSize=3"

# 登录（mock 微信：任意 code 即建号）
curl -X POST http://127.0.0.1:8080/api/auth/login \
  -H "Content-Type: application/json" -d '{"code":"test1"}'
```

| 地址 | 用途 |
|---|---|
| http://localhost:8080/swagger-ui/index.html | Swagger API 文档 |
| http://localhost:8080/actuator/health | 健康检查 |

---

## 四、环境配置

### 配置文件优先级
```
application.yml（通用）
  └── application-dev.yml（默认，本地开发）
  └── application-prod.yml（生产，SPRING_PROFILES_ACTIVE=prod）
```

### 密钥
- 本地：`.env.local`（已 gitignore，含 MySQL 密码）
- 生产：环境变量注入，缺项会被 `StartupValidator` 拒绝启动

### 生成生产密钥
```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 32   # PASSWORD_PEPPER
```

---

## 五、常见问题

| 现象 | 原因 | 解决 |
|---|---|---|
| 启动报 `Could not resolve placeholder` | `.env.local` 未加载 | 用 `./start-dev.sh` 或手动 `set -a; source .env.local; set +a` |
| 启动报 `ClassNotFoundException: XxxMapper` | target 残留旧类 | `mvn clean compile` 后重启 |
| Flyway 报校验失败 | 迁移脚本被改动 | 开发环境可 `flyway repair`，或核对 `flyway_schema_history` |
| 小程序报「网络错误」 | 未勾选不校验域名 | 工具「详情→本地设置」勾选 |
| 小程序报 `App is not defined` | 用 node 直接跑了 | 必须用微信开发者工具编译运行 |
| 端口 8080 被占用 | 旧实例未退出 | 见下方停止方式 |
| Maven 拉依赖失败 | 用了默认 settings | 必须加 `-s settings-mirror.xml`（工作区 `.m2` + 阿里云镜像）|

### 停止后端

```bash
# 若用受管任务启动：直接终止该任务
# 或按端口清理
PID=$(ss -tlnp | grep 8080 | grep -oE 'pid=[0-9]+' | cut -d= -f2 | head -1)
kill $PID
```

---

## 六、项目结构速览

```
treatbord/
├── start-dev.sh              ← 启动脚本
├── settings-mirror.xml       ← Maven 镜像配置（勿提交）
├── .env.local                ← 本地密钥（勿提交）
├── database/                 ← schema.sql / seed.sql（手工基线）
├── docs/                     ← 设计/安全/接口/方案文档
├── src/main/java/com/treatbord/
│   ├── common/               ← Result/异常/脱敏工具
│   ├── config/               ← Web/MyBatis/OpenAPI/启动校验
│   ├── security/             ← JWT/拦截器/限流/密码
│   └── module/<业务>/        ← auth user task submission review ...
├── src/main/resources/
│   ├── application*.yml      ← 多环境配置
│   └── db/migration/         ← Flyway V1~V4
└── miniprogram/              ← 微信小程序（16 个页面）
```

---

## 七、演示账号（本地 mock 登录）

| code（登录用）| 身份 |
|---|---|
| `xiaomei` | 小美（发布者，ADMIN）|
| `xiaoming` | 小明（接取者）|
| 任意字符串 | 自动创建新用户 |

> mock 登录由 `treatbord.wx.enabled=false` 控制（仅开发环境）。
