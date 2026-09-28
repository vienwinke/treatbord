# 文档 ↔ 代码 一致性核对表

> 生成时间：2026-09-27 · 数据均为**现场实测**（命令见"核对方式"列），非人工填写。
> 用途：答辩/简历中引用的每个数字都能在此对上代码；改动后请重跑本文末尾的核对脚本。

## 一、规模类

| 项目 | 文档声明 | 实测 | 核对方式 | 一致 |
|---|---|---|---|---|
| 后端 Java 文件 | 116 | 116 | `find src/main/java -name '*.java' \| wc -l` | ✅ |
| 后端代码行数 | 6.8k | 6895 | `find … -exec cat {} + \| wc -l` | ✅ |
| REST 接口数 | 34 | 34 | 统计 `@GetMapping/@PostMapping/@PutMapping/@DeleteMapping` | ✅ |
| Controller 数量 | — | 12 | `find src/main/java -name '*Controller.java'` | — |
| 小程序页面数 | 16 | 16 | `app.json` 的 `pages` 长度 | ✅ |
| 业务表数量 | 14 | 14（不含 `flyway_schema_history`） | `information_schema.tables` | ✅ |
| 设计文档数 | 7 | 7 | `ls docs \| wc -l` | ✅ |
| 自动化测试用例 | 93 | 93（20 个测试类） | `mvn test` 输出 / `find src/test -name '*Test.java'` | ✅ |
| 测试类数量 | 20 | 20 | 同上 | ✅ |

## 二、数据库迁移

| 项目 | 声明 | 实测 |
|---|---|---|
| 迁移脚本 | V1~V8（8 个文件） | V1__init_schema.sql V2__seed_config.sql V3__file_sec_status.sql V4__add_user_account.sql V5__add_missing_config_keys.sql V6__add_claim_scan_indexes.sql V7__add_ratelimit_config_keys.sql V8__add_login_lock_config_keys.sql |
| `flyway_schema_history` | 8 条全部成功，最新 V8 | 演练中实测：`共8条 最新=V8 成功=8` ✅ |

## 三、本轮新增能力 ↔ 代码位置

| 能力 | 代码位置 | 接口/入口 | 文档 |
|---|---|---|---|
| 文件签名 URL | `module/file/service/FileUrlSigner.java` | `signedUrl()` / `verify()` | README·API_DESIGN·LOG(B5) |
| 读时签名（授权=上传者+接取双方） | `module/submission/service/SubmissionViewAssembler.java` | 凭证详情/发布者接取列表 | API_DESIGN 附录 |
| 签名 TTL | `application-*.yml` `signed-url.ttl-seconds` | **1800
30 秒** | RUNBOOK·LOG |
| 审计查询 | `module/admin/service/AdminService#listAuditLogs` | `GET /api/admin/audit-logs` | API_DESIGN 附录 |
| 登录失败锁定 | `security/LoginAttemptService.java` | 登录接口 429 | README·DB_DESIGN+RUNBOOK |
| 任务缓存 | `module/task/service/TaskCacheService.java` | 列表/详情读写 | DB_DESIGN 配置键 |
| 限流 Lua 原子化 | `security/RateLimitService.java` | 所有受限接口 | LOG(B4) |
| 限流覆盖面 | `security/RateLimitInterceptor.java` | **9 个场景** | DB_DESIGN 配置键 |
| 业务指标 | `config/BusinessMetrics.java` | `/actuator/prometheus`（**6 个指标定义**） | README·RUNBOOK |
| 慢接口日志 | `config/SlowRequestLoggingFilter.java` | 阈值 `slow-api-ms` | RUNBOOK |
| 慢 SQL 日志 | `config/SlowSqlInterceptor.java` | 阈值 `slow-sql-ms` | LOG(B6 踩坑记录) |

## 四、验证类（演练实测数据）

| 项目 | 数据 | 来源 |
|---|---|---|
| 200 并发抢 5 名额 | 成功 5 / 名额已满 195 / **零超卖** | 压测（B7-1） |
| 写接口 P95（200 并发） | 288.6ms（P50 215.2ms） | 同上 |
| 读接口吞吐/P95 | 3205 req/s / 25.7ms（50 并发 1000 请求） | 同上 |
| 备份体积/耗时 | 44KB / 0.06s | 演练（B7-2） |
| 恢复耗时（RTO） | **1.06s** | 同上 |
| 灾后一致性 | 15/15 表行数一致，Flyway 8/8 | 同上 |
| 测试全绿 | 93 用例 / 0 失败 | `mvn test` |

## 五、核对脚本（改动后重跑）

```bash
cd treatbord
echo "Java 文件: $(find src/main/java -name '*.java' | wc -l)"
echo "接口数:    $(grep -rhoE '@(Get|Post|Put|Delete|Patch)Mapping' src/main/java --include=*Controller.java | wc -l)"
echo "页面数:    $(python3 -c "import json;print(len(json.load(open('miniprogram/app.json'))['pages']))")"
echo "迁移脚本:  $(ls src/main/resources/db/migration | wc -l)"
mvn -s settings-mirror.xml test | grep -E 'Tests run: [0-9]+, Failures'
# 过期表述自查（应无输出）
# README 与活文档必须无输出（开发日志/学习记录等过程文档已移出仓库，不参与检查）
grep -rn "V1~V4\|32 个接口\|16 个页面\|104 个 Java\|5.5k 行" \
  README.md docs/API_DESIGN.md docs/DB_DESIGN.md docs/RUNBOOK.md docs/SECURITY_REVIEW.md
```
