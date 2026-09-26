# 学习路线与进度报告

> 生成时间：2026-09-26
> 配套文档：[`LEARNING_PLAN.md`](LEARNING_PLAN.md)（6 周路线 · 09-25 制定）· [`LOG.md`](LOG.md)（项目开发日志）· [`ROADMAP.md`](ROADMAP.md)（项目阶段）
> 数据来源：本机 DSH 会话投影缓存（18 条会话）、各项目仓库与 git 历史、`LearnProject/docs` 笔记。
> **本文所有结论都标注了出处，可自行复核；未使用任何估算或猜测值。**（复核命令见文末附录）

---

## 一、一句话现状

从 **09-02 到 09-26 的 24 天**里，你产出了 **6 个可运行的代码资产**（Treatbord、苍穹外卖改造、Java 练习集、数据可视化作业、数学建模 C 题、房价预测项目）和 **7 份求职材料**；而 `LEARNING_PLAN.md` 的闭卷自评是 **26/100**。

这个落差就是最值得记住的结论：

> **你的问题不是"没干活"，而是"干过的活没有变成能张口就答的知识"。**

`LEARNING_PLAN.md` 里自己写的诊断（"知识碎片化 + 二手知识，看 AI 写、没自己推导"）与下面的证据完全吻合——这份报告的作用是把那句话变成可核对的事实。

---

## 二、真实时间线

按 DSH 会话记录还原（时间已换算为本地 UTC+8）：

| 日期 | 工作区 | 主题 | 实际产出 | 归类 |
|---|---|---|---|---|
| 09-02 | treatbord | 项目启动（3 个会话） | `API_DESIGN.md` / `DB_DESIGN.md` / `SECURITY_REVIEW.md` 成文；后端骨架动工 | 主线起点 |
| 09-03 | treatbord | 登录页 UI + 提取代码 | 首个提交 `init: 后端 P0-P4 + 小程序前端`；登录页改版、任务大厅重做、全局色板统一 | 主线 |
| 09-03 | day4 | 查看当前环境 | （该目录现已不存在） | 支线 |
| 09-04 | day5 | 读取文件 / 第一轮筛选卷考生卷背景 | （该目录现已不存在） | 支线 |
| 09-07~09-10 | sky_take_out | 无会话，纯 git 产出 | **你自己的 5 次提交**，全在员工模块（见 §3.3） | 基本功 ⭐ |
| 09-10 | mytest | 测试 | 环境验证 | 环境 |
| 09-12 | ~ | 连通性测试 ×2 | 0 token，纯环境自检 | 环境 |
| 09-14 | treatbord / venv | 工具链答疑 · Python 多环境 | 环境梳理（本会话即该日创建，09-26 续用） | 支线 |
| 09-15 | 作业2 | 根据 PDF 完成作业 | 数据可视化作业（notebook + dashboard） | 支线 |
| 09-16~09-17 | LearnProject | Java 系统学习代码点评 | **7 份求职材料** + 8 个手写 Java 练习（见 §3.2、§3.4） | 求职主线 ⭐ |
| 09-19 | LearnProject | （空会话，0 token） | — | — |
| 09-20 | 实训作业/C题 | 数学建模 C 题问题一数据处理 | 问题一.zip + 3 张图 + 模型比较表（见 §3.5） | 支线 |
| 09-25 | treatbord | P0 上线必备改造 | **6 项 P0 安全改造** + 4 份新文档 + 3 次提交（见 §3.1） | 主线 ⭐ |
| 09-25 | ~code | 测试 | 环境验证 | 环境 |
| 09-26 | treatbord | 本会话续用：DSH 0.1.7 桌面版 | 桌面客户端 + Command Code 模型通道打通（见 §3.6） | 工具链 |

**观察**：09-02~09-04 是"项目爆发期"，09-07~09-10 转向"基本功 + 教程项目"，09-16~09-17 集中做求职材料，09-20 之后是作业/建模，09-25 回到项目收口。**这条轨迹是"想做项目 → 发现自己不会 → 转去补基础 → 又回到项目"，方向是对的，但缺乏并行推进。**

---

## 三、产出清单（"进程"的硬证据）

### 3.1 主线：Treatbord

| 指标 | 数值 | 出处 |
|---|---|---|
| 后端 Java 文件 | **104** | `find src -name '*.java' \| wc -l` |
| REST 接口 | **32** | [`LOG.md`](LOG.md) §四 |
| 数据表 | **14** | [`LOG.md`](LOG.md) §一 |
| 小程序页面 | **16** | `find miniprogram -path '*pages*' -name '*.js'` |
| git 提交 | **10** | `git rev-list --count HEAD` |
| 配套文档 | **9** 份（不含本报告） | `docs/` |

已完成范围：P0 骨架+认证 → P1 任务+接取（原子扣减防超卖）→ P2 凭证+通知+审核+定时任务 → P3 文件+互评+举报 → P4 管理端+结算 → P5 小程序前端 → P5.1~P5.5 补页/核验/UI/联调。

**09-25 的 P0 上线改造（6 项，全部自测通过）**——这是目前质量最高的一段工作，因为它有"发现问题 → 定位根因 → 修复 → 验证"的完整闭环：

1. 敏感字段泄露（`openid` / `passwordHash` 明文出接口）→ VO 隔离 + `@JsonIgnore` + 日志脱敏
2. 单文件配置 + 生产默认密钥 → 拆分 dev/prod + `StartupValidator` 缺密钥拒启
3. 仅本地磁盘存储 → `AliOssStorageService` 条件装配
4. 自研 SHA-256 哈希 → BCrypt + pepper，**且兼容旧格式、登录时自动升级**
5. 内容安全为占位 → 接入微信 `msgSecCheck` / `mediaCheckAsync` + fail-open 降级
6. 无合规页 → 用户协议 + 隐私政策页

### 3.2 Java / Spring 基本功（手写练习，非看视频）

来自 `LearnProject/src`、`SpringMavendemo`、`mycode`：

| 练习 | 文件 | 对应知识点 |
|---|---|---|
| 动态代理 | `Logger.java` / `LoggerImpl.java` / `LoggerProxy.java` | **AOP 底层原理**（手写，不是调 API） |
| 反射 | `ReflectionDemo.java`、`reflect/ReflectDemo.java` | 反射 |
| 并发 | `ConcurrencyPitfallTest.java`、`ThreadDetectDemo.java` | 并发陷阱 / 线程检测 |
| IoC 雏形 | `ioc-demo/IocDemo.java`、`InterfaceDemo.java` | **手写容器**（对应 `LEARNING_PLAN` W2 交付物） |
| 集合 / 多态 | `HashMapData.java`、`AnimalDemo.java` | 集合体系 / 多态 |
| 框架演进 | Springdemo（Gradle 5 文件）→ SpringMavendemo（Maven 13 文件 + `reflect/`）→ bookif（25 文件） | Spring 入门 → Maven → 小项目 |

> 这一栏很重要：`LEARNING_PLAN` 的 W1/W2 交付物（手写集合、手写 IoC 容器）**你其实已经开始做了**，只是没意识到它算进度。

### 3.3 苍穹外卖（sky_take_out）：教程项目 + 自主改造

- 规模：**142** 个 Java 文件；三模块 `sky-common` 29 / `sky-pojo` 50 / `sky-server` 63；git 共 66 次提交
- **其中属于你的 5 次提交（09-07 ~ 09-10）**，全部集中在员工模块，且是一条清晰的递进线：

| 日期 | 提交 | 学到的东西 |
|---|---|---|
| 09-07 | 动手练习前的备份 | 先备份再改（好习惯） |
| 09-07 | 新增 `getByPhone` 接口 | 简单查询 |
| 09-08 | `getByPhone` 改为抛异常 + 全局异常处理 | **异常体系** |
| 09-08 | 完成员工模块：`getByName`、用户名查重 | 业务校验 |
| 09-10 | 新增 `EmployeeVO` 解决敏感字段泄露；JOIN 与事务实战 | **VO 隔离 + JOIN + 事务** ⭐ |

> 注意最后一条：`EmployeeVO` 解决敏感字段泄露——这与 Treatbord 的 P0-1 是**同一个知识点**，说明你已经在"迁移应用"了。

### 3.4 求职材料（09-16 一个会话产出 7 份）

`LearnProject/docs/`：`Java基础速查.md`、`并发速查.md`、`Spring速答卡.md`、`八股速答卡.md`、`面试提示卡.md`（主管面·腾讯会议）、`面试答题手册.md`、`面试话术本.md`。

其中你自己写的诊断最值得反复看：

> 「**5 道题里 4 道你都答出了核心，但都漏了一半。** 面试没有"追问三次"的机会，所以必须练**一次答全**。」
> 「⚠️ **不要照着念** —— 念稿子面试官听得出来。」

⚠️ 但要清醒看一个数据：那一场会话 **输入 15.9 万 token、输出 70.4 万 token**（全 18 场里输出/输入比最悬殊的一场）。也就是说这批材料**主要是 AI 写、你来读**——这正是 `LEARNING_PLAN` 说的"二手知识"。材料本身很好，但它不等于你会。

### 3.5 支线：Python / 数据 / 数学建模

| 项目 | 产出 |
|---|---|
| `实训作业/作业2` | 数据可视化作业：notebook + `dashboard.png` + 导出预览 HTML + 原 PDF |
| `实训作业/文琨作业day1` | 第一次作业（`mycode.py` + 作业说明 md） |
| `实训作业/C题` | 数学建模 C 题问题一：数据处理 + 混合效应模型比较 |
| `实训作业/figures` | `fig1_个体趋势.png`、`fig2_残差诊断.png`、`model_compare.csv` |
| `PycodeProject/House-price` | 房价预测（sklearn） |
| `实训作业/d7`、`9.19` | 20 个 notebook + `data.py`（sklearn / pandas / plotly / streamlit） |

数学建模的模型比较（`model_compare.csv` 原文）：

```
模型    AIC                BIC                说明
m1     702.354503393909   727.2268979490342  随机截距
m2     700.138790605216   729.9856640713663  随机截距+交互项
```

> m2 的 AIC 更低但 BIC 更高（多了参数被惩罚）——**这正好是一个可以拿去面试讲的"模型选择权衡"案例**，比背八股有说服力。

### 3.6 工具链（09-26 今天）

- DSH 升级到 0.1.7-rc.2，装好官方桌面版（Windows 侧），打通 Command Code 模型通道
- 踩坑与教训：`desktop` profile 由 Electron 独占、CLI 拒绝管理；profile 未开 HMR 时改配置必须重启；Command Code 的 key 必须落在**当前活跃账号槽位**对应的 ref 上
- ⚠️ **事故一次**：一张 API key 明文进入了会话记录并落盘到 `session.v4.jsonl.zstd`（未加密）→ 待轮换

---

## 四、进度对照

### 4.1 投入量（投影缓存口径）

| 指标 | 数值 |
|---|---|
| 会话数 | 18 |
| 累计输入 token | ≈ **808 万** |
| 累计输出 token | ≈ **249 万** |
| 最重的三场 | 09-03 登录页 UI（425 万 / 44.8 万）· 09-02 项目启动（245 万 / 44.1 万）· 09-16 Java 学习（15.9 万 / **70.4 万**） |

> 口径说明：数字取自 `~/.dsh/storages/session_projcache/`，**当前会话（09-14 创建、09-26 续用）未完全计入**，实际总量更高。

### 4.2 对照 6 周计划的实际位置

| 周 | 计划主题 | 实际问题 | 判断 |
|---|---|---|---|
| W1 | Java 基础 + MySQL 核心 | 未开始（计划 09-25 才制定，今天才 09-26） | ⬜ 但集合/并发/反射/动态代理已有手写练习 |
| W2 | Spring 核心（IoC/AOP/MVC/事务） | 未开始 | ⬜ `ioc-demo` 是雏形；**事务是最大空白** |
| W3 | Spring Boot + MyBatis-Plus + Redis | **已在 Treatbord 完整用过** | 🟡 见过 ≠ 会写 |
| W4 | 项目核心模块重写 | 未开始 | ⬜ 当前 104 个文件主要由 AI 写成 |
| W5 | 测试 + 工程化 | 未开始，且 `src/test` 目录**不存在** | ❌ 建议提前 |
| W6 | 面试冲刺 | 材料已有（09-16），缺"答全"训练 | 🟡 半成品 |

**结论：W3/W4/W5 的知识你"见过"（项目里全都有），W1/W2 才是真正的空白——这与 26/100 的自评完全一致，说明你的自评是准的，不要怀疑它。**

### 4.3 三条有证据的短板

1. **零测试** —— `src/test` 目录**根本不存在**（`find src/test` → No such file or directory），而项目已有 32 个接口。
   → 计划把测试排在 W5，但它其实是**验证"你是真会还是假会"的唯一手段**，建议提前到 W1 就开始，每个 demo 配一个断言即可。

2. **耦合与事务边界不清** —— `LOG.md` 自己审计出：task 模块被跨模块 import **64 处**、全项目仅 **7 处** `@Transactional`。
   → 症状是"能跑通但说不清边界在哪"，正对应 W2 的事务专题。**这两个数字本身就是极好的面试素材**（"我发现我的项目有 64 处跨模块直接引用，说明分层没守住，我的重构方案是…"）。

3. **表达"答不全"** —— 09-16 的自我诊断：4/5 题答出核心但漏一半。
   → 这不是知识缺口，是**结构缺失**。建议每天用计划里的"复述法 + 三层追问"把当天主题讲成 3 分钟，录音对比。

---

## 五、接下来 7 天（W1 落地版）

沿用 [`LEARNING_PLAN.md`](LEARNING_PLAN.md) §8 的日程，但**补上"当天可验证的产出"**——没有产出就不算过完这一天：

| 天 | 主题 | 当天必须交出的东西 | 项目对照点 |
|---|---|---|---|
| D1 | 集合体系 | 手写 ArrayList 简化版（含扩容），能讲清 fail-fast | Treatbord 里的 `List/Map` 用法 |
| D2 | HashMap 原理 | 手写 put/get（数组 + 链表），画一遍扩容流程 | `Map.of()` / `Collectors.toMap` |
| D3 | 泛型 + Stream | 用 Stream 重写 Treatbord 里一段 for 循环 | `TaskService` |
| D4 | 异常体系 | 不看代码，自己写一套 `BusinessException` + 全局处理器 | `common/GlobalExceptionHandler` |
| D5 | 并发基础 | 多线程抢票 demo **复现超卖**，再用原子 SQL 修掉 | `TaskMapper.incrementClaimedCount` ⭐ |
| D6 | MySQL 索引 | 造 10 万行数据，`EXPLAIN` 对比有无索引 | `database/schema.sql` 的索引设计 |
| D7 | 事务与隔离级别 | 复现脏读/不可重复读；逐个检查现有 7 处 `@Transactional` 的边界 | `ClaimService.claim()` / `ReviewService.review()` ⭐ |

**本周唯一硬指标**：`practice/` 目录下 ≥ **7 个能跑的 demo** + 每天一段 3 分钟脱稿复述（录音）。

---

## 六、路线修正建议（3 条）

1. **把测试从 W5 提前到 W1**：不是"补测试"，是"用测试判断自己是否真懂"。每写一个 demo 配一个断言，成本极低。
2. **把 W4「项目模块重写」拆一件到现在做**：选 `TaskMapper.incrementClaimedCount()` 这一个方法，**闭卷重写 + 并发压测**。它是整个项目里技术密度最高的 20 行代码，拿下它等于拿下 W4 的缩影。
3. **求职线不要停**：09-16 那批材料改成"每天一题、答全为止"的复述训练（对着录音讲，回听找漏点），比再生成新材料有效得多。

---

## 附录：数据口径与复核命令

```bash
# 1. 会话时间线 / token 用量（投影缓存）
cd ~/.dsh/storages/session_projcache/sessions
ls -l                      # 18 个 session-*.json
#    字段：record.identity.createdAt/cwd、record.rows.title.val、
#          record.rows.titleInput.val、record.rows.tokenUsage.val.totals

# 2. 项目规模
cd ~/Project/treatbord
find src -name '*.java' | wc -l                    # 104
ls src/test                                        # No such file ← 零测试
find miniprogram -path '*pages*' -name '*.js' | wc -l   # 16
git log --oneline | wc -l                          # 10

cd ~/Project/sky_take_out
git log --date=short --pretty='%ad | %an | %s' -5  # 你自己的 5 次提交

# 3. 学习笔记与练习
ls ~/Project/LearnProject/docs/                    # 7 份求职材料
find ~/Project/LearnProject/src -name '*.java'     # 8 个手写练习
ls ~/Project/实训作业/figures/                      # 建模产出
```

**口径声明**
- 时间统一为本地时间 UTC+8；会话时间取自投影缓存的 `createdAt`（该会话**创建**时间），最后活动时间见对应 json 文件 mtime。
- token 数为投影缓存快照，**未含当前会话的后续部分**，故为下限。
- `src/test` 为 0、64 处跨模块 import、7 处 `@Transactional` 三项数据引自 [`LOG.md`](LOG.md) 中你自己的审计记录，未重新统计。
- 09-03 / 09-04 的 `day4`、`day5` 工作区当前已不存在于 `~/Project`，仅有会话标题可考，故仅作时间线记录、不参与结论。
