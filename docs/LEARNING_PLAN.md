# Treatbord 学习计划（项目驱动 · 高强度版）

> 基线：2026-09-25 技术栈测试 **26/100**
> 投入：每天 6-8 小时 · 目标：**6 周内达到 Java 后端面试水平**
> 方法：以本项目为载体，**自己动手写**，AI 只做答疑与 Code Review

---

## 一、现状诊断

```
Java 基础    ████░░░░░░░░░░░░░░░░   4/12
Spring       ████░░░░░░░░░░░░░░░░   4/20   ← 最大短板（面试占 40%）
MyBatis      ██████████░░░░░░░░░░   6/12   ← 相对最好
MySQL        ██████░░░░░░░░░░░░░░   5/16
Redis        ███░░░░░░░░░░░░░░░░░   2/12
安全         ████████░░░░░░░░░░░░   5/12
Maven        ░░░░░░░░░░░░░░░░░░░░   0/8
小程序       ░░░░░░░░░░░░░░░░░░░░   0/8
```

**核心问题**：知识碎片化（偏门点反而会，主干全空）+ 二手知识（看 AI 写，没自己推导）。

---

## 二、总路线（6 周）

| 周 | 主题 | 目标分数 | 交付物 |
|---|---|---|---|
| **W1** | Java 基础 + MySQL 核心 | 35+ | 手写集合/并发 demo、SQL 练习 |
| **W2** | **Spring 核心**（IoC/AOP/MVC/事务）| 55+ | 手写简易 IoC 容器、事务 demo |
| **W3** | Spring Boot + MyBatis-Plus + Redis | 65+ | 自己写一个完整 CRUD 模块 |
| **W4** | 项目核心模块重写（防超卖/状态机/JWT）| 72+ | 3 个模块自己实现 |
| **W5** | 补测试 + 工程化（Maven/CI）| 78+ | 单测 + 集成测试 |
| **W6** | 面试冲刺（八股 + 项目答辩 + 简历）| 80+ | 项目讲稿 + 面试题库 |

> W4-W6 可与投简历并行（边投边补）。

---

## 三、每日节奏（6-8 小时）

```
上午（3h）  理论学习
  ├─ 2h  看视频/书/官方文档（一个主题，别贪多）
  └─ 1h  写笔记：用自己的话总结（不抄原文）

下午（2.5h）项目对照
  ├─ 在 Treatbord 里找到这个知识点的**实际代码**
  ├─ 逐行读懂 + 写注释说明"为什么这么写"
  └─ 记录疑问清单（当天解决）

晚上（2h）动手写
  ├─ 1.5h 合上所有资料，自己写一个最小 demo 或改造项目代码
  └─ 0.5h 对比项目原实现，找差距，记录到笔记

睡前（0.5h）复习
  └─ 默写当天核心概念（不看资料）
```

**铁的纪律**：
- ❌ 不许只看视频不动手（看 8 小时 = 0 收获）
- ❌ 不许直接让 AI 写代码（先自己写，写完再让它 review）
- ✅ 每天必须产出代码（哪怕 20 行）

---

## 四、项目模块 → 知识映射表

学每个主题时，直接对照项目里的代码：

| 主题 | 项目中的对应代码 | 优先级 |
|---|---|---|
| **Spring IoC/DI** | 所有 `@Service`/`@Component` + `@RequiredArgsConstructor` | P0 |
| **Spring 自动配置** | `config/MybatisPlusConfig`、`config/WebConfig`、`@ConditionalOnProperty` | P0 |
| **Spring MVC 拦截器** | `security/AuthInterceptor`、`security/RateLimitInterceptor` | P0 |
| **ThreadLocal** | `security/UserContext` | P0 |
| **事务管理** | `ClaimService.claim()`、`ReviewService.review()` 的 `@Transactional` | P0 ⭐ |
| **MyBatis-Plus** | `TaskMapper`（注解 SQL）、`MybatisPlusConfig`（分页插件）| P0 |
| **乐观锁** | `Task.version` + `@Version` | P1 |
| **逻辑删除** | 所有实体的 `@TableLogic deleted` | P1 |
| **MySQL 索引** | `database/schema.sql` 里的唯一索引/复合索引 | P0 ⭐ |
| **并发控制** | `TaskMapper.incrementClaimedCount()` 原子 SQL + CAS | P0 ⭐⭐ |
| **Redis 应用** | `TokenBlacklistService`（黑名单）、`RateLimitService`（限流）| P0 |
| **JWT** | `JwtUtil`、`AuthInterceptor` | P0 |
| **密码安全** | `PasswordEncoder`（BCrypt + pepper）| P1 |
| **全局异常处理** | `common/GlobalExceptionHandler` | P0 |
| **参数校验** | 各 DTO 的 `@NotBlank/@Size` + `@Valid` | P1 |
| **定时任务** | `schedule/ScheduledTasks` | P1 |
| **统一响应** | `common/Result`、`PageResult` | P1 |
| **多环境配置** | `application-dev.yml` / `-prod.yml` + `StartupValidator` | P1 |
| **AOP 思想** | （项目用拦截器实现，可自己写一个 `@Aspect` 日志切面练习）| P2 |

---

## 五、每周验收（自测机制）

每周日做三件事：

1. **闭卷自测**：让 AI 按本周主题出 10 道题，≥80 分算过
2. **脱稿讲解**：挑一个知识点，对着白板/AI 讲 5 分钟（讲不清 = 没学会）
3. **代码产出**：本周写的代码提交到 `practice/` 目录（**必须能跑**）

---

## 六、学习方法（三件套）

### 1. 复述法
看完一段内容 → **合上资料** → 用自己的话讲出来（可录音/写下来）→ 对比原文找差距

### 2. 默写法
学完一个知识点 → **不看代码**手打一遍 → 对比项目原实现 → 记录差异

### 3. 追问法
每个知识点至少追问三层：
```
是什么？（如：@Transactional 是声明式事务）
为什么？（为什么需要它？不用会怎样？）
边界？（什么时候失效？有什么坑？）
```

---

## 七、AI 的使用边界（重要）

| 场景 | 允许 | 不允许 |
|---|---|---|
| 学概念 | ✅ 让 AI 解释原理、举例 | ❌ 只让 AI 给答案不思考 |
| 写代码 | ✅ 自己写完让 AI review | ❌ 直接让 AI 写完整功能 |
| 卡壳时 | ✅ 自己查 30 分钟后再问 | ❌ 一遇到问题就问 |
| 验收 | ✅ 让 AI 出题、批改 | ❌ 自欺欺人地"看懂了" |

**目标**：6 周后，你能**自己**实现项目里任何一个模块（不看 AI 写的代码）。

---

## 八、W1 详细日程（可立即执行）

| 天 | 上午（理论）| 下午（项目对照）| 晚上（动手）|
|---|---|---|---|
| D1 | Java 集合：List/Map/Set 体系 | 看项目里 `List/Map` 的实际用法 | 手写 ArrayList 简化版 |
| D2 | HashMap 原理（数组+链表+红黑树）| 看 `Map.of()`、`Collectors.toMap` 用法 | 手写简易 HashMap（put/get）|
| D3 | 泛型 + Stream API | 看 `TaskService` 里的 Stream 用法 | 用 Stream 重写一段 for 循环 |
| D4 | 异常体系 + 自定义异常 | 看 `BusinessException` + `GlobalExceptionHandler` | 自己写一套异常+处理器 |
| D5 | 并发基础：线程/锁/原子类 | 看 `TaskMapper` 原子 SQL（数据库层并发）| 写多线程抢票 demo（体验超卖）|
| D6 | MySQL 索引原理（B+树/最左前缀）| 看 `schema.sql` 的索引设计 | 造 10 万数据，用 EXPLAIN 对比有无索引 |
| D7 | MySQL 事务与隔离级别 | 看 `@Transactional` 用法 | 写 demo 复现脏读/不可重复读 |

**W1 验收**：能讲清 HashMap 原理、能写出异常处理体系、能解释索引为什么快。

---

## 九、进度追踪

- [ ] W1 Java 基础 + MySQL
- [ ] W2 Spring 核心
- [ ] W3 Spring Boot + MyBatis-Plus + Redis
- [ ] W4 项目核心模块重写
- [ ] W5 测试 + 工程化
- [ ] W6 面试冲刺

> 每周日更新此清单，并记录自测分数。
