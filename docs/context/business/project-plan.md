# ReleaseHub 愿景与产品规划

> 更新：2026-11-21。正式生命周期与责任读取 [唯一项目画像](../PROJECT_PROFILE.yaml)，阶段核对依据见 [AIEF 接入记录](../../../tasks/records/2026-11-21-aief-project-operating-context.md)。
> 本文维护愿景、问题定义和产品边界；实现及验证事实见 [项目台账](../../project-ledger.md)，当前任务只从 [执行路线图](../../execution-roadmap.md) 的唯一 HEAD 选择。

## 1. 愿景与产品定义

**让团队围绕一次发布，共享一份明确的发布计划，并把跨仓库集成的执行过程变成可控、可解释、可追溯、可安全重试的过程，减少对个人记忆和临场协调的依赖。**

ReleaseHub 是发布窗口驱动的跨仓分支与集成编排系统，当前面向 GitLab。长期目标是成为发布节奏、发布范围和执行记录的单一事实源。

“单一事实源”有明确责任边界：

- ReleaseHub 保存发布意图：窗口、参与迭代、仓库范围、分支计划、执行顺序、触发主体和策略。
- GitLab 提供远端事实：分支、提交、MR、审批、流水线和合并结果。
- ReleaseHub 将发布意图、实际动作与 GitLab 证据对应起来；缺少证据时保留未知状态，不用本地状态代替远端事实。

## 2. 要解决的问题

一次业务发布是整体，但改动、分支和执行事实分散在多个仓库；同一个仓库又可能被多个迭代共同影响。团队需要持续把分散事实重新组织成可执行、可检查的发布计划。

| 成本 | 具体问题 | 产品责任 |
|---|---|---|
| 认知成本 | 不清楚一次发布包含哪些迭代、影响哪些仓库、每仓要集成哪些分支 | 以窗口统一展示范围、仓库并集及仓库内迭代队列 |
| 协调成本 | 多人、多迭代共享仓库，范围变更和合并顺序依赖口头约定 | 明确绑定、计划、顺序与阻塞传播规则，配置变更和执行分离 |
| 执行成本 | 逐仓建分支、找 MR、合并、检查状态容易漏项或重复 | 显式编排、MR-first、幂等动作和完整执行记录 |
| 故障判断与恢复成本 | 请求超时、进程退出或落库失败后，不知道远端是否已经执行 | 保存意图和证据，区分明确失败与未知结果，只在证据允许时重试或继续 |

系统应当让用户从同一发布上下文中回答：这次发什么、执行到哪里、为什么卡住、下一步该处理什么。

## 3. 主要使用者

| 使用者 | 需要达成的结果 |
|---|---|
| 发布负责人 / 项目管理人员 | 明确发布时间、发布范围和当前整体状态 |
| 技术负责人 / 研发人员 | 核对分支计划，按仓库集成改动，定位阻塞并执行安全重试 |
| 测试人员 | 看到窗口所含迭代、仓库及实际集成结果，明确待测内容 |
| 配置维护人员 | 维护仓库、GitLab 连接及必要命名规则 |

角色描述表达使用场景，不构成自研 RBAC 或审批平台的需求。

## 4. 核心业务关系

- ReleaseWindow 关联多个 Iteration。
- Iteration 关联多个 Repository，仓库允许重叠。
- 窗口仓库集合为所关联迭代仓库的并集。
- 同一迭代可被其他窗口引用；单个窗口的关闭不能擅自终结全局迭代或删除共享分支。

两个关键执行单位：

| 执行单位 | 责任 |
|---|---|
| (window, repository) | 确保该窗口的 release 分支，承载仓库级收尾 |
| (window, iteration, repository) | 将指定迭代的 feature 分支集成到窗口的 release 分支 |

例如：窗口 W 包含迭代 A、B；A 修改订单和支付仓库，B 修改订单和管理前端仓库。窗口计划应包含三个仓库，订单仓库依次集成 A、B；不按迭代重复创建互相竞争的仓库流程。

## 5. 当前产品主线

维护者 v2.6 中的 MVP/Must/Won’t 描述已确认的产品范围基线，不定义项目生命周期，也不把已有产品重置成首次原型建设。当前任务按已授权完整蓝图推进，不能仅因“研发中”自行缩小验收范围。

完整用户路径为：规划窗口 → 关联迭代与仓库 → 预览范围及顺序 → 显式触发编排 → 查看结果和阻塞 → 按证据处理并精确重试。

必须具备：

1. 窗口、迭代、仓库及绑定管理，正确表达共享仓库。
2. 窗口视角按仓库选择迭代集合，预览真实分支计划与稳定顺序。
3. 确保 feature/release 分支，采用 ENSURE_MR → TRY_MERGE 的 MR-first 集成。
4. 不同仓库可独立推进，同一目标仓库的迭代合并串行执行。
5. Run/Item/Step 记录实际范围、顺序、结果、阻塞原因及 GitLab 证据。
6. 重试只作用于明确失败或阻塞的选定执行项，已成功动作不重复制造副作用。
7. 窗口页可查看迭代、仓库并集、各迭代合并状态及有效重试入口。

默认命名为 feature/{iterationKey}、release/{windowKey}；ref 默认来源为仓库默认分支。覆盖能力必须有明确配置来源并进入计划，不能由执行器临时猜测。

## 6. 不可破坏的不变量

### 配置与执行分离

Attach/Detach 建立或解除关系，不写 Git；Publish 改变窗口生命周期，不隐式启动编排。默认 MANUAL，用户明确触发 Orchestrate 后才进行 Git 写操作。未来 AUTO_ON_ATTACH 如实现，必须显式开启并复用同一受理路径。

### 顺序与失败范围

仓库内默认按 attachAt 及稳定次序执行，并记录实际顺序。默认 FAIL_FAST：明确失败或阻塞后，后续同仓迭代标记 SKIPPED_DUE_TO_BLOCK；其他仓库继续。CONTINUE_ON_BLOCK 只能显式选择。结果 UNKNOWN 时，即使选择 CONTINUE，也不能继续该仓后继写入。

### 事实与幂等

分支已存在、MR 已存在、合并已完成都有明确结果语义。HTTP 成功、时间戳、分支存在、CI 已触发都不能单独证明整个发布成功。必须保留 project ID、分支、MR IID/URL、提交 SHA、窗口/迭代及阻塞原因等可核实证据。

结果未知不得伪装为明确失败后重放。恢复时使用原计划和已保存事实；数据库回滚、任务超时和租约到期不能撤销已经发出的 Git 请求。

### 生命周期与收尾

业务 CLOSED 与收尾结果分别展示。关闭受理、Run 和不可变计划原子提交；重复关闭返回原回执，不重新执行。共享 feature/release 和 Iteration 保留；Tag 使用已确认提交，CI 默认关闭且需显式选择。收尾自动恢复须单独验收。

## 7. 范围取舍

当前优先完成跨仓集成及其可靠性闭环：

- 一期只使用 GitLab REST API，不 clone 仓库进行 Git push。
- Maven/Gradle 版本更新扩展按 v2.6 放在二期；现有能力的必要正确性与安全修复继续按已授权蓝图推进，不把功能存在本身当成扩大范围的依据。
- 不自动解决冲突；读取必要的审批、流水线、权限和保护分支状态，解释阻塞。
- 不扩展为自研审批或 CI 编排平台。
- 通知、RBAC、跨 Provider 平台化及无当前消费者的通用任务/DAG 基础设施不进入当前主线。

新增对象、接口或设计之前，必须核查现有职责能否承载、是否符合目标，以及是否有实际消费者。研发阶段不保留旧执行入口、兼容写路径或双轨实现。

## 8. 如何判断产品是否达成目标

| 场景 | 可观察的验收标准 |
|---|---|
| 共享仓库的多迭代发布 | 窗口仓库并集完整，计划没有遗漏或重复，仓内顺序可核对 |
| 配置调整 | Attach/Detach/Publish 零 Git 写，不因保存配置意外执行发布 |
| 正常集成 | 每个执行单位有可追溯结果，完成状态有真实 MR/提交证据 |
| 单仓阻塞 | 原因及证据可见；默认只阻止同仓后继，其他仓库能够完成 |
| 重复操作 | 同一受理意图返回原记录，不重复创建计划或远端副作用 |
| 中断和响应丢失 | 原计划及已确认事实保留；未知结果不盲目重发，继续必须有证据 |
| 用户观察 | 页面、API、导出使用同一事实，能够说明当前状态和可执行的下一步 |

这些是目标标准。测试、设计标记或表结构仅用于验证是否达到目标，不能代替目标本身。

## 9. 当前实施路线与事实来源

2026-11-21 已验收并推送 S3-A/B1、B2 和 C1：纯配置边界、权威计划、MR-first、统一状态、精确执行项选择、仓库级安全收尾，以及唯一 WINDOW 持久四动作。C1 最后候选契约、前端回归与静态复扫通过，固定提交 `e5094ba` 的 [Full-Link CI](https://github.com/tongsh6/release-hub/actions/runs/44899945002) 为 5/5。真实 UI 黄金旅程按完整恢复蓝图另行验收，不用后端 API/GitLab fixture 代替。

- 已交付的 C2-A：受理事务保存冻结范围、顺序和四动作，HTTP 202 返回回执；唯一单任务执行器由有界 worker 与数据库游标扫描消费。数值项目 ID、MR IID/源 SHA 检查点及 Task/Step/Item/Run/绑定/guard 的原子结果约束沿用 C1。
- C1 的历史中断验收：当时 HTTP 200、请求线程驱动，MR 检查点提交后强杀只能证明安全停住、重启同 key 零 HTTP。C2-A 的新独立进程证据进一步证明默认后台扫描继续原 PENDING TRY_MERGE，成功前缀不变、MR POST 仍 1 次、MERGE PUT 恰 1 次；不依赖重复受理来唤醒。
- C2-A 已完成本地验收并分组推送，固定 `1761572bca6c149aecba78d26a7b25cb98b5251a` 的 [Full-Link 44905843754](https://github.com/tongsh6/release-hub/actions/runs/44905843754) 为 5/5；最后消费者契约补验 16/16。页面保存原请求并只读观察原 Run/Task，PENDING 重启继续的范围已验收，不能据此宣称 C2 整体完成。
- 已交付的 C2-B1「无 operation 的过期 RUNNING 接管」已通过独立本地门禁和固定 SHA CI：仅接管期限已到、领取事实完整，且该 task 没有任何 git_operation 或自身 Step 的 RUNNING；Run→Task 锁内重核并按原 token CAS，保留首次领取时间及已有实际顺序，缺失顺序不补造。PENDING 与 CLAIMED_UNWRITTEN 两个独立 JVM 硬杀场景均保持原 Item/Task/成功 Step，MR POST=1、MERGE PUT=1；任何已有 INTENT/SEND/APPLIED/UNKNOWN 均不自动恢复。
- B1 静态整理前的冻结门禁为 API 48/48、普通后端 680 项（675 PASS/5 既有 SKIP）；两处 SQL 表达方式与构造器标注整理后，最终组合再次 76/76（PG 31、HTTP 33、进程 2、worker 7、scope 3），各轮源码 hash 前后一致，不混为同一次运行。最终静态 `20261121-214959` 的 6 份新鲜 SpotBugs XML 均为 0，typecheck PASS、lint 0 errors/8 既有 fixture warnings。默认 lease 60 分钟、无心跳；V40 与 JPA 增加可空期限列，NULL 不回填为接管资格，local 用例只验证新列。没有 UI/API 改动，前端 214 为 C2-A 沿用基线，本片仅重跑 lint/typecheck。后端 `3f810c8` 与文档 `5441df6` 已推送，固定 `5441df6bdc971953dee390c9470d4786fe0eab44` 的 [Full-Link 44911634004](https://github.com/tongsh6/release-hub/actions/runs/44911634004) 为 5/5，B1 已 DONE；该独立 CI 证据不扩大 Slice4/5 或真实 UI 旅程的验收边界。
- 已交付的 C2-B2「建分支成功证明与 APPLIED 结果投影恢复」已完成本地门禁和阶段推送，固定 `cc800362f333418776f4a816e1fa74ee9f08c04f` 的 [Full-Link 44917254215](https://github.com/tongsh6/release-hub/actions/runs/44917254215) 为 5/5。正常创建与恢复共用严格的 201/name/commit.id 证明；原分支结果在进程中断后可由同一事务补齐，保留 token、期限和 HTTP 历史。独立 JVM 验证投影阶段 HTTP=0、分支 POST 总数 1；同一源码树的门禁及提交证据见[事实台账](../../project-ledger.md)和[本轮记录](../../../tasks/records/2026-11-21-sa-013-orchestration-consistency.md)。后续只从[路线图](../../execution-roadmap.md)的唯一队首推进。
- 已交付的 C2-B3 实现 CREATE_MR 完整证明及原 ENSURE_MR 检查点恢复：正常适配器、HTTP 证据记录与恢复使用同一判定；纯 DB 投影保留原任务、token、期限及 HTTP 历史，后继 TRY 消费原 IID/SHA。2026-11-22 最终定向 403/403、API 48/48、普通 738 项（733 PASS/5 既有 SKIP）及静态 `20261122-001110` 通过，七份 backend hash 快照一致。真实进程在 APPLIED 后、Task 结果前被强杀，重启补投影零 HTTP，整个场景 MR POST=1、MERGE PUT=1。后端 `d87dfa8`、文档 `544983b` 已推送，固定 `544983beff74604852c050209e4c8caa93678ef2` 的 [Full-Link 44924378741](https://github.com/tongsh6/release-hub/actions/runs/44924378741) 为 5/5，B3 已 DONE。
- 已交付的 C2-B4「合并 MR 完整证明与末尾原任务结果恢复」已完成本地验收、分组推送与固定 SHA CI：定向 533/533、API 48/48、普通 789 项（784 PASS/5 既有 SKIP）及静态 `20261122-010845` 通过，七份 backend 快照一致。正常调用、规范事件和恢复共用 200/merged 证明；合法 null 合并 SHA 不被补造，原结果事务仅更新结果字段。无 PENDING 的末尾任务经默认 worker 恢复，真实 exit137 后投影 HTTP=0、原合并 PUT 总数 1。后端 `23950f9`、文档 `be1ccbe` 已推送，固定 `be1ccbe61a5155ba5ce8582f884d41dbb385a932` 的 [Full-Link 44929301318](https://github.com/tongsh6/release-hub/actions/runs/44929301318) 为 5/5，B4 已 DONE。
- 已交付的 C2-B5 实现原 MERGE_MR 尚未发送意图的安全续发，并完成独立 RED→GREEN、本地及固定 SHA 远端验收：现有 Task 承担当前发送和结果权，原 operation/attempt/guard 保留历史归属；完整 typed 请求与稳定指纹一致才沿原执行器发送一次，SEND 后不接管。真实 JVM 强杀、再次在 APPLIED 后强杀及旧 worker 竞争已验证。相关 692 项为分批汇总，API 48/48、普通 816 项（811 PASS/5 既有 SKIP）及静态门禁通过，七份最终 495 文件源码快照一致。后端 `064282887ae825255360bcf3b04d0377163219aa` 与文档 `62d075c` 已分组推送，固定该后端 SHA 的 [Full-Link 44935216788](https://github.com/tongsh6/release-hub/actions/runs/44935216788) 为 5/5，B5 已 DONE。PUT 前读取的已包含原 SHA/明确阻塞只保留现有安全回滚边界，不宣称已自动收口；证据与剩余范围见本轮记录第 18 节。
- 已交付的 B6 完成原完整未发送 MERGE 的本地收口：锁内唯一 seq0、当前 fence 与同事务 NOT_SENT/Task 终态证明本次未发送，已 SEND 无响应时拒绝提前完成。ALREADY_MERGED/MERGE_BLOCKED/UNKNOWN 保持业务语义；竞争、真实 CAS/外层回滚、独立 JVM 与组件消费者已验证，相关单次 749/API 48/普通 816、前端 217 及静态通过。实现 `4e992e9`、文档 `29ec005` 已推送，固定实现完整 SHA 的 [Full-Link 44940092094](https://github.com/tongsh6/release-hub/actions/runs/44940092094) 为 5/5，B6 已 DONE，完整证据见记录 19.9。
- 已交付的 B7 完成原 CREATE_BRANCH 未发送意图的安全接续与本地收口，仅 ENSURE_FEATURE/ENSURE_RELEASE。复用现有 TaskClaim/Context、Operation 与 prepare/begin/sending，绑定原项目 I、消费原 SHA A 和经批准的请求；快照不替代实时 DB 权限。目标已存在于 B≠A 仍可 ALREADY_EXISTS，I→J 可保留真实 FAILED；APPLIED 保持 B2 原响应证明。独立 RED 后，定向 91/91、单次相关 840/840、API 48/48、普通后端 831（826 PASS / 5 既有 SKIP）、静态及最终源码绑定均已通过；实现 00def779 已推送。固定实现 SHA 的 Full-Link 44945748427 attempt 2 为 5/5，B7 已 DONE；首轮 429 与最终边界见记录 20.8–20.9，验收文档单独收口。
- B8 正常 CREATE_MR 完整请求的发送证明闭环已验收：正常 ENSURE_MR 和已有非 Task cleanup 沿现 producer → Journal begin/sending 核准 → 返回 Operation 重建实际 HTTP body → B3 响应与原结果消费者工作。独立新例 125/125、单次相关 965/965、API 48/48、普通 872 PASS / 5 既有 SKIP 及静态通过；实现 `537588e` 已推送，固定 SHA 的 Full-Link 44972473174 为 5 PASS / 0 FAIL。无新实体、表、schema、DTO、Port 或用户入口；B8 未开放 CREATE_MR 恢复，历史 B3 APPLIED 不加新完整请求/hash 门禁。当前 B9 先独立核对原 CREATE_MR INTENT 按原始 SHA 的安全接续与本地收口，之后处理 SEND/UNKNOWN；源码和新测试尚未修改。
- S4/S5：其他动作的安全恢复、完整 GitLab/UI 旅程和受控交付。

完整阶段依赖、迁移、风险及验收条件统一维护在 [恢复设计](../../openspec/changes/add-durable-run-recovery/design.md) 与 [任务追踪](../../openspec/changes/add-durable-run-recovery/tasks.md)，不在本文维护第二份执行队列。

| 文档 | 承担的责任 |
|---|---|
| [项目画像](../PROJECT_PROFILE.yaml) | 正式生命周期、身份、责任与稳定约束的唯一事实源 |
| 本文 | 愿景、问题定义、已确认产品范围与取舍 |
| [项目台账](../../project-ledger.md) | 当前实际实现、验证范围及风险 |
| [执行路线图](../../execution-roadmap.md) | 唯一队首与后续任务 |
| [场景验收矩阵](../../reports/scenario-acceptance-matrix.md) | 用户场景与证据 |
| [工程治理准则](../tech/architecture/ai-engineering-governance.md) | 设计、实现和验证方法 |
| [研发记录](../../../tasks/records/2026-11-21-sa-013-orchestration-consistency.md) | 本轮代码、反例、验证和提交证据 |

旧 v3.2 文档中的“10 种执行器已完成”“Publish 自动编排”“版本更新是 MVP 核心”及旧 Run 状态不能作为当前规范或完成证据。历史演变通过 Git 历史与 [项目变更日志](../../PROJECT_PROGRESS.md) 追溯。
