# 最终代码总检报告（2026-09-27）

> ⚠️ **时点快照**：本文是 2026-09-27 那次总检的现场记录。
> 文中数字（文件数 / 接口数 / 页面数 / 迁移范围 / 用例数）**不随代码演进更新**，
> 当前数字一律以 [CONSISTENCY_CHECKLIST.md](CONSISTENCY_CHECKLIST.md) 为准。

## 一、检查结果总览

| 检查项 | 方法 | 结果 |
|---|---|---|
| 编译 | `mvn -o clean test` | **BUILD SUCCESS** |
| 自动化测试 | 93 个用例（离线） | **93/93 通过** |
| 硬编码凭证 | `git grep` 全仓扫描 | **0 命中** |
| 调试残留 | `System.out` / `TODO` / `console.log` / `printStackTrace` | **各 0** |
| SQL 注入面 | `${}` 拼接扫描 | **0** |
| 失效注解 | `@Aspect`（项目无 aspectjweaver） | **0** |
| 生产配置 | `application-prod.yml` 审查 | springdoc 关闭 · 签名强制 · 密钥全走 env · 11 项启动校验 |
| 一致性数字 | 现场实测 | 115 文件 / 33 接口 / 15 页面 / V1~V8 / 93 用例 |
| 仓库卫生 | tracked 文件扫描 | 移除 2 个不该入仓文件 |

## 二、本次总检发现并修复的真 bug（1 个）

**现象**：`BusinessMetricsTest.reviewMetrics` **必现失败**（3/3）——`treatbord.review.wait` Timer 未创建。

**根因（两层）**：

1. `task_claim.submitted_at` 是**秒精度** `datetime`，MySQL 写入时会**四舍五入**（`22:58:41.6` → `22:58:42`）；
   紧接着审核时算差值得到 `waitSeconds = -1`，而我的守卫写的是 `waitSeconds >= 0` → **样本被静默丢弃**。
   → 后果：**指标会随机消失**。排障时"看不到数据"会被误读成"没问题"，比抛异常更危险。
2. `registry.timer(name, "description", "...")` 中的 `"description"` 被 Micrometer 当成了**标签键**，
   meter 上挂了假标签 `description=凭证提交到审核的等待时长`。

**修复**：
```java
// 修复前：waitSeconds >= 0 才记录 → 负值样本丢失
// 修复后：
long seconds = Math.max(0L, waitSeconds);          // 时间精度噪声按 0 计，不丢样本
Timer.builder("treatbord.review.wait")
     .description("凭证提交到审核的等待时长（秒）")   // 正确设置描述，不再污染标签
     .register(registry).record(Duration.ofSeconds(seconds));
```

**验证**：修复前 3/3 失败 → 修复后 3/3 通过 → 全量 **93/93 绿**。

**三条教训（值得记住）**：
1. **观测代码的守卫要保守**：宁可记 0，不可丢样本；静默丢数据比报错更难发现
2. **秒精度 DATETIME 做时间差必须容忍负值**（写入四舍五入 + 时钟抖动）
3. Micrometer 的 `timer(name, tags...)` 中 varargs 是**键值标签对**，不是描述

## 三、仓库清理（2 个不该入仓文件）

| 文件 | 处理 | 原因 |
|---|---|---|
| `miniprogram/project.private.config.json` | `git rm --cached`（本地保留）+ `.gitignore` | 微信"私有配置"（本地 `urlCheck`/热重载/调试条件），官方约定不入仓 |
| `miniprogram/.cloudbase/container/debug.json` | `git rm` + 磁盘删除 | 云开发容器调试残留，项目未使用云开发 |

## 四、遗留与风险

| 项 | 级别 | 说明 | 归属 |
|---|---|---|---|
| 部署产物 | ✅ 已补齐 | `deploy/`：Dockerfile · compose · Nginx · 备份脚本 · 告警规则（CI 验证） | 已完成 |
| 类目资质未确认 | **P0** | 上架硬门槛 | 你（外部） |
| 微信真实凭证未接（AppID/Secret） | P1 | 现走 mock 登录 / 内容安全 | 你（外部） |
| demo 库 `treatbord` 停在 V4 | P2 | 下次以该库启动会自动迁移到 V8 | 无需处理 |
| Redis 抖动式不可达（GitHub 推送同源问题） | P3 | 推送需重试 | 网络环境 |
| 学习线（算法 0 题 / 讲稿未录音） | — | 学习记录已移出仓库（本地保留） | 你我 |
