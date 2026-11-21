# Project Ledger / 项目事实台账

> **新会话强制读取入口**：任何新会话 AI 进入本项目，必须先读本文件。
> 它替代不了 `docs/PROJECT_PROGRESS.md`（变更日志），但提供「事实基线」：
> 当前任务在做什么、什么已经验证过、什么不要再做。正式生命周期、身份与责任只由 [项目画像](context/PROJECT_PROFILE.yaml) 维护；本文件记录实现和验证证据。
> 维护节奏：每个 release commit 必须更新「已验证 / Top Priority / 关键证据」三段。
> 当用户要求“挑任务执行”时，必须再读取 `docs/execution-roadmap.md`，只执行其中唯一 `HEAD` 队首任务。

---

## 1. 当前阶段目标

2026-11-21：用户已授权持续研发、必要重构和分阶段推送；生命周期与工作模式的正式值读取项目画像，AIEF 校准依据见 [接入记录](../tasks/records/2026-11-21-aief-project-operating-context.md)。main/develop 同指 `d4534cc`，研发位于 `feature/orchestration-consistency`。S3-A/B1、S3-B2、S3-C1 已分阶段推送；AIEF 对齐提交 `83998b2`，CI profile/Python 修复为 `08432e9`、`a7181fa`。C1 后端 `44e742c`、前端 `3766686`、E2E/脚本 `4c6b3e9`、文档 `e5094ba` 均已核对远端；固定 `e5094ba` 的 [Full-Link 44899945002](https://github.com/tongsh6/release-hub/actions/runs/44899945002) 5/5 通过。**S3-C2-A 已完成并推送**：后端 `06aee3b`、前端 `b37397f`、E2E/脚本 `e0c6356`、文档 `1761572`；固定完整 SHA `1761572bca6c149aecba78d26a7b25cb98b5251a` 的 [Full-Link 44905843754](https://github.com/tongsh6/release-hub/actions/runs/44905843754) 为 5/5。**S3-C2-B1 已 DONE 并推送**：后端 `3f810c8`、文档 `5441df6`；固定完整 SHA `5441df6bdc971953dee390c9470d4786fe0eab44` 的 [Full-Link 44911634004](https://github.com/tongsh6/release-hub/actions/runs/44911634004) 为 5/5，feature 与远端一致，main/develop 仍为 `d4534cc`。**S3-C2-B2 已 DONE 并推送**：`fe4c006`、`1386ccc`、`cc80036`；固定 `cc800362f333418776f4a816e1fa74ee9f08c04f` 的 [Full-Link 44917254215](https://github.com/tongsh6/release-hub/actions/runs/44917254215) 为 5/5。2026-11-22，C2-B3 已按 `d87dfa8`、`544983b` 分组推送，固定 `544983beff74604852c050209e4c8caa93678ef2` 的 [Full-Link 44924378741](https://github.com/tongsh6/release-hub/actions/runs/44924378741) 为 5/5，已 DONE。C2-B4 已按后端 `23950f9`、文档 `be1ccbe` 分组推送，固定 `be1ccbe61a5155ba5ce8582f884d41dbb385a932` 的 [Full-Link 44929301318](https://github.com/tongsh6/release-hub/actions/runs/44929301318) 为 5/5，已 DONE。当前工作按[执行路线图](execution-roadmap.md)进入 C2-B5「原 MERGE 未发送意图的安全续发」的独立契约与 RED 阶段；尚未实现 B5，C2 整体尚未完成。

C2-A 已交付基线由有界后台 worker 消费唯一持久四动作，Orchestrate/retry 返回 HTTP 202/Run ID；相同 key 只返回原回执。C2-A 当时只扫描当前 WINDOW 协议的 PENDING，不接管 RUNNING/UNKNOWN。同 Run 双仓真实 HTTP 验证已通过：R1 响应在途时 R2 可进展；默认定时器与独立 JVM 重启只继续原 PENDING TRY_MERGE，MR POST=1、MERGE PUT=1。普通后端 677 项（672 PASS/5 既有 SKIP），API/数据库 67/67、HTTP 29/29、进程 1/1，后补 worker 停机断言后 7/7；重叠 suite 不累计。前端 39 文件 214/214、全部门禁与静态复扫通过；最后 E2E 消费者契约补验 16/16 已包含在 `e0c6356`。C2-A 固定 SHA Full-Link 5/5 已收口。B1 已交付的限定接管范围、本地与固定 SHA CI 证据见下文；后续写意图恢复和真实 UI 黄金旅程仍待独立验收。

C2-B1 已交付范围仅接管期限已到、领取事实完整，且该 task 没有任何 git_operation 或自身 Step 的 RUNNING；Run→Task 锁内重核并按原 token CAS，保留首次领取时间及已有实际顺序，缺失顺序不补造。默认 lease 60 分钟、无心跳，NULL 期限不回填；任何已有 INTENT/SEND/APPLIED/UNKNOWN 均排除在自动接管外。静态整理前 API 48/48、普通后端 680 项（675 PASS/5 既有 SKIP）通过；最终 SQL 与构造器整理后组合再次 76/76，各轮源码 hash 前后一致，不混称同一次运行。PENDING/CLAIMED_UNWRITTEN 两个独立 JVM 硬杀均退出 137，原 Item/Task/成功 Step 保持，MR POST=1、MERGE PUT=1。最终静态 `20261121-214959` 通过，8 条既有 fixture warnings 已逐项记录保留理由。没有 UI/API 改动，前端 214 仅沿用 C2-A 基线；后端 `3f810c8`、文档 `5441df6` 已推送，本片固定 SHA Full-Link 44911634004 为 5/5，B1 交付闭合。

C2-B2 已完成实现、本地门禁和远端交付。定向189/189、API48/48、普通后端700项（695 PASS/5既有SKIP）与静态`20261121-224714`全部通过；三个测试阶段及静态扫描的backend hash是同一稳定树，无静态修复后的源码偏差。三组提交 `fe4c006`、`1386ccc`、`cc80036` 已推送，本片固定 SHA Full-Link 44917254215 为 5/5，标记 DONE。范围仅为 ENSURE_FEATURE/ENSURE_RELEASE 的 CREATE_BRANCH：正常成功证明统一为 HTTP 201、文本类型且匹配的 name、确切 commit.id；规范事件必须与原 operation 属于同一 attempt。投影恢复保留原 Task 标识、token、期限和 HTTP 历史，新 Step 使用当前 worker 的 Clock；只在 Run→Task→guard 结果事务内补原投影，不 GET/POST，不兼容缺少完整证明的旧摘要。上述为 B2 交付时的范围；B3 当前 MR 创建恢复事实见下文。

实现前初始 RED 证据：`.ai/reports/validation/c2-b2/initial-red-jdk21/result.json` 与本轮任务记录15.1。使用JDK21，adapter/business两阶段均backend sourceStable=true，全部0 ERROR；采集脚本退出0不代表测试通过，重叠suite不累计。

| 实现前初始 RED 范围 | 实际结果 | 当时复现的缺口 |
|---|---|---|
| 正常客户端完整类 | 55项：52 PASS／3 FAIL／0 ERROR | HTTP200/202/206即使正文完整仍被误当创建成功 |
| 真实HTTP→Task | 9项：9 FAIL／0 ERROR | 错误commit.id被object.id/commit.sha覆盖后任务COMPLETED/CREATED、operation APPLIED且guard释放；反向正确字段被干扰成UNKNOWN，规范字段持久化也有缺口 |
| PostgreSQL原任务投影 | 18项：15 PASS／3 FAIL／0 ERROR | 两个ENSURE动作与事务回滚正例无法投影；错误/缺失证明、资格及UNKNOWN owner先提交反例保持拒绝 |
| 独立JVM中断重启 | 1项：1 FAIL／0 ERROR | APPLIED后、completeTask前硬杀；重启正常运行但ENSURE_FEATURE持续RUNNING、后三项PENDING |

最终本地实现已验证：合法40/64位SHA按值忽略大小写比较冻结target.ref，branch/sourceRef精确匹配；规范证明与原operation同attempt。补投影不重读当前ref或改向namespace，不转移token、不续租、不改HTTP历史，新Step仅记录当前Clock的处理起止。扫描复用既有四动作中建分支任务必有PENDING后继的条件，未扩候选SQL，不扩任意末尾任务或MR恢复。

| C2-B2 最终本地门禁 | 结果 | 证据与边界 |
|---|---|---|
| 定向联合 | 189/189，0 FAIL/ERROR/SKIP | `.ai/reports/validation/c2-b2/initial-green-jdk21/result.json`；7类 |
| API | 48/48，0 FAIL/ERROR/SKIP | `.ai/reports/validation/c2-b2/final-gates/result.json` |
| 普通后端 | 700项＝695 PASS＋5既有SKIP，0 FAIL/ERROR | 同上；重叠suite不累计 |
| 稳定源码 | 定向前后、API前后、普通前后、静态后共7份hash映射均为同一495文件集合 | `initial-green-jdk21/source-*.json`与`final-gates/`各阶段快照，无静态整理后源码偏差 |
| 静态复扫 | 六份新鲜SpotBugs XML均0，typecheck PASS，lint 0 errors/8既有fixture warnings | `.ai/reports/static-scan/20261121-224714/summary.md`逐项记录保留理由、复扫证据及风险 |
| 独立JVM APPLIED_BRANCH | run `bec9cda6-3f2c-4f7c-b40a-b828d939f979`，killedPid13969/exit137、resumedPid13978；projectionHttp0，branchPost1/mrPost1/mergePut1，preservedToken=true、preservedEvents=3 | `initial-green-jdk21/maven.log`的PROCESS_APPLIED_BRANCH_RESUME；零HTTP限定纯DB投影阶段，后继由原执行器推进；另两类重启场景本轮也PASS |
| 固定 SHA 远端验收 | `cc800362f333418776f4a816e1fa74ee9f08c04f`；run 44917254215 / job 136885413516，SUCCESS，Passed 5 / Failed 0；日志北京时间 2026-11-21 23:13:25 | [Full-Link 44917254215](https://github.com/tongsh6/release-hub/actions/runs/44917254215)；`.ai/reports/validation/c2-b2/full-link/result.json`、`136885413516.log`。后端 API＋隔离 GitLab，不扩大 Slice4/5 或完整 UI 覆盖 |

本片本地验收、分组推送及固定 cc800362 的 Full-Link 5/5 均已完成，未借用 B1 的 CI。前端无变更，214项仅为C2-A沿用基线，本轮实际重跑lint/typecheck；真实UI黄金旅程及Slice4/5既有覆盖边界保留。

### C2-B3 已交付验收（2026-11-22）

创建 MR 的成功证明已统一为 HTTP 201 与原始响应的同仓数值项目身份、准确分支、正整数 IID、有效 URL、与冻结 target.sha 等值的 40/64 位 SHA 及 opened。正常适配器、HTTP Journal 与恢复共用 `confirmedCreatedMr`；CREATE 检查点不携带 mergeSha。原 ENSURE_MR 的 APPLIED 结果在 Run→Task→guard 同一事务内补投影，保留原 token、期限、首次领取时间、实际顺序和 HTTP 历史，使用当前 Clock 写新 Step；只补一个任务后返回。沿用四动作的 PENDING TRY 后继发现候选，未扩扫描 SQL。

| C2-B3 最终本地门禁 | 实际结果 | 证据与边界 |
|---|---|---|
| 定向联合 | 403/403，11 类，0 FAIL/ERROR/SKIP | `.ai/reports/validation/c2-b3/final-green-v2-jdk21/result.json` |
| API | 48/48，0 FAIL/ERROR/SKIP | `.ai/reports/validation/c2-b3/final-gates-v2/api/result.json` |
| 普通后端 | 738 项＝733 PASS＋5 既有 SKIP，0 FAIL/ERROR | `final-gates-v2/ordinary/result.json`；suite 成员与既有基线一致，重叠 suite 不累计 |
| 稳定源码 | 定向前后、API 前后、普通前后及静态后七份快照相同，均 495 文件 | `final-green-v2-jdk21/source-*.json` 与 `final-gates-v2/`；生产代码在 371→403 轮未改，只修正两份旧 fixture |
| 静态复扫 | 六份新鲜 SpotBugs XML 均 0，typecheck PASS，lint 0 errors/8 既有 fixture warnings | `.ai/reports/static-scan/20261122-001110/summary.md` 逐项记录保留理由与覆盖边界 |
| 独立 JVM APPLIED_MR | run `40bf6c0a-0914-4783-85a9-8978b3b9ccc4`，PID 41103→41112，exit137；projectionHttp0、branchPost0、mrPost1、mergePut1，原 token/3 条 HTTP 事件保持 | `final-green-v2-jdk21/maven.log`；零 HTTP 仅指补检查点阶段，原 TRY 后继正常访问 GitLab；四类中断重启本轮均通过 |

初始 RED、365/371 中间 GREEN、SHA 表示一致性反例及首轮普通回归 718 项（2 FAIL/1 ERROR/5 SKIP）均保留在本轮记录第 16 节。旧测试夹具已按原本的保护、审批与隐私目标改成完整 201 响应，再独立完成上述门禁，未降低生产成功判据。B3 后端完整纵向切片已本地提交 `d87dfa87751713b2a8a146bdb8062ef06c9571ce`（16 文件，`feat(run): recover confirmed MR creation checkpoints`）；验收文档 `544983beff74604852c050209e4c8caa93678ef2` 与后端均已推送，本片固定 SHA Full-Link 44924378741 为 5/5，B3 已 DONE。后续 B4 的独立验收事实见下节。MR 后续变为 closed 或 source 前进不改写历史创建事实；原 TRY 再按原 SHA 的 target containment 和原 IID 决定结果。B3 交付不覆盖合并 MR、INTENT/SEND/UNKNOWN、版本/收尾恢复或完整 UI 旅程。

B3 远端验收原始证据：`.ai/reports/validation/c2-b3/full-link/result.json` 和 `136907137667.log`；run 44924378741 / job 136907137667 的 headSha 为 `544983beff74604852c050209e4c8caa93678ef2`，SUCCESS、Passed 5 / Failed 0，日志北京时间 2026-11-22 00:34:33，收尾成功。[Full-Link 44924378741](https://github.com/tongsh6/release-hub/actions/runs/44924378741) 使用后端 API＋隔离 GitLab，仍不扩大 Slice4/5 或完整 UI 覆盖。

### C2-B4 已交付验收（2026-11-22）

MERGE_MR 的正常调用、HTTP 规范事件和恢复共用严格 HTTP 200/merged 证明：三个原 JSON 数值项目身份、原 IID、文本分支/URL/source SHA，且 merge_commit_sha 必须存在、允许显式 null 或合法 SHA。精确分支由既有 workflow/原检查点绑定；不能用其他 SHA 或字段别名补造成功。

末尾 TRY_MERGE 没有 PENDING 后继时，已有扫描只增加完整到期、MERGE_MR/APPLIED 且无自身 Step 的候选。原 Run→Task→guard 结果事务内核对唯一操作、同 attempt seq2、原 PUT/IID/冻结 SHA、计划/检查点和所有权，纯 DB 补原 MERGED。复用正常 TRY 的四字段结果 patch，保留原身份字节、disposition、token、期限、首次时间、实际顺序和 HTTP 历史；新 Step/lastMergeAt 使用当前处理 Clock。

| C2-B4 最终本地门禁 | 实际结果 | 证据与边界 |
|---|---|---|
| 定向联合 | 533/533；11 suites，0 FAIL/ERROR/SKIP | `.ai/reports/validation/c2-b4/final-green-jdk21/result.json` |
| API | 48/48；3 suites，0 FAIL/ERROR/SKIP | `final-gates/api/result.json` |
| 普通后端 | 789 项＝784 PASS＋5 既有 SKIP；58 suites，0 FAIL/ERROR | `final-gates/ordinary/result.json`；重叠 suite 不累计 |
| 稳定源码 | 七份 hash 快照及当前源码一致，均 495 backend 文件 | `local-evidence-audit.json`；仅两个测试文件在首轮 GREEN 后改断言 |
| 静态 | 六份新鲜 SpotBugs XML 均0，typecheck PASS，lint 0 errors/8 既有 warnings | `.ai/reports/static-scan/20261122-010845/summary.md` 已逐项说明保留理由及覆盖边界 |
| 独立 JVM 末尾 MERGE | run `fb7e11f7-9256-408b-b530-e0cd657a2f3a`，PID89472→89518、exit137；无 PENDING，projectionHttp0、MR POST1/mergePUT1，原 token/6条事件保持 | `final-green-jdk21/maven.log`；5类进程中断全部通过，MR404/target重置为受控HTTP fixture |
| 固定 SHA 远端验收 | `be1ccbe61a5155ba5ce8582f884d41dbb385a932`；run 44929301318 / job 136922204638，SUCCESS，Passed 5 / Failed 0；日志北京时间 2026-11-22 01:28:09 | [Full-Link 44929301318](https://github.com/tongsh6/release-hub/actions/runs/44929301318)；`.ai/reports/validation/c2-b4/full-link/result.json`、`136922204638.log`；后端 API＋隔离 GitLab，保留 Slice4/5 与完整 UI 覆盖边界 |

独立 RED 在生产仍为77f206c时完成：adapter105/20FAIL、workflow/app77PASS、HTTP34/28FAIL、进程3/1FAIL、PG44/12FAIL，均0ERROR/0SKIP。5项额外PG轮确认旧结果字段确实存在，5FAIL且0ERROR；与44项重叠不累计。第一次冻结导出因测试调用参数错误而编译失败、0用例，不作为行为RED。首轮 GREEN 为533/2FAIL/0ERROR；仅修正测试将无序 Item 集合位置当执行顺序、以及旧候选断言混淆“发现”和“领取”的问题，保留完整历史后得到最终533/533。详见任务记录17及 `.ai/reports/validation/c2-b4/`。

后端 `23950f98f2c7000bc054e74e9a67feb5f892e2dc`（13 文件）与验收文档 `be1ccbe61a5155ba5ce8582f884d41dbb385a932`（10 文件）已分组提交并推送；后端提交树与本地门禁相同。本片准确 SHA 的 Full-Link 5/5 已完成，B4 标记 DONE，未借用 B3 CI。main/develop 仍同指 d4534cc，远端仅保留两条基线分支及当前 feature。真实 SQL keyset、双恢复者和外层事务回滚各按实际覆盖记录；未声称坏前页整个 worker 强杀演练或确定的双恢复者锁等待 trace。

C2-B5 的独立设计已核实：现有 Task 承担当前发送/结果权，原 operation、attempt 和 guard reservation 保留历史归属；原 MERGE 请求缺少完整 typed 参数及跨 JVM 稳定指纹，须先补 producer 再允许续发。现阶段只有契约和消费者查重，尚无 B5 RED/GREEN 或交付证据。原已发送、未知结果、其他动作与完整 UI 继续独立追踪，详见记录 18。

### 2026-10-08 历史 S2 验收记录

2026-10-08：SA-013 S1、S2 已完成，SA-018 真实类型门禁已修复。S2 已交付原子 Git 操作、独立证据与仓库保留、只读核对 API/页面；在新建 GitLab 17.11.3 项目完成分支/MR、回滚阻断和 Provider 能力验证。默认不开启生产保护模式。后端 356 PASS / 5 SKIP、定向集成 48 PASS、前端 111 PASS；静态复扫 0 项，前端保留 8 个存量 warning。当前 HEAD 为 S3 可恢复执行范围评审，S3–S5 实施未批准。实际边界见 `docs/openspec/changes/add-durable-run-recovery/s2-implementation.md`；报告见 `tasks/records/2026-10-08-sa-013-s2-implementation.md`。

### 2026-10-05 历史推进记录

2026-10-05 已完成 SA-017 首次部署管理员初始化与登录修复，并修正 PostgreSQL 18.1 卷路径。后端单元/架构检查 328 PASS / 5 SKIP，新旧登录集成 10 PASS，Compose 空库启动及重建持久化通过。SA-004 生产加密默认配置、错钥阻断、隔离库备份恢复也已完成；最终后端检查 332 PASS / 5 SKIP，定向集成 14 PASS。SA-018 构建环境与 CI 门禁也已完成：pnpm 10.33.0、Node 24、develop/PR 触发声明及本地前端检查通过，锁文件不变。SA-013 中断恢复设计及 6 项缺口证据已完成，生产恢复逻辑未实现；当前 HEAD 为中断恢复提案评审与 S1 范围确认。加密证据见 `tasks/records/2026-10-05-sa-004-production-token-crypto.md`。完整范围和证据见 `tasks/plans/2026-10-05-initial-admin-bootstrap.md`、`tasks/records/2026-10-05-sa-017-initial-admin-bootstrap.md`。

### 历史发布候选基线（2026-05-24）

以下保留历史阶段说明；当前任务选择以路线图为准，SA-002 历史迁移边界继续有效。

**v0.1.11 受控发布候选交付证据已收口，SA-002 迁移进入执行计划评审门禁**。
- 主线：按 `docs/reports/scenario-acceptance-matrix.md` 和 `docs/reports/release-candidate-2026-05-23.md` 推进；SA-001 受控发布候选 dogfood/staging 验证、发布候选评审页、交付证据收口，以及 SA-002 数据质量人工复核处置策略、受控处置执行审计设计、处置 case 最小实现、处置 case 页面验收、BranchCreationMode 独立迁移服务设计、proposal 评审门禁和最小 dry-run 均已完成；当前优先进入 BranchCreationMode 执行计划评审门禁
- 不做：新功能（RBAC、通知、CI 深集成）；不重构 Iteration 领域到 `repoAssociations`

### 场景化用户旅程自动化原则（AI 接手必读）

- ReleaseHub 业务数据必须由前端用户旅程创建或变更：分组、仓库纳管、迭代、发布窗口、挂载、冲突扫描、冲突解决、发布编排、版本更新等，不得用 API 或数据库脚本偷造前置业务状态后再声称完成用户旅程。
- 外部环境 fixture 可以由脚本准备：GitLab/Postgres/Backend/Frontend 服务、GitLab seed repo、GitLab PAT、基础容器状态。这些不是 ReleaseHub 用户在系统内完成的业务动作。
- API、数据库、GitLab 查询只能作为证据复核：用于证明页面动作后的后端约束、Run 记录、分支、提交、版本写回成立，不能替代前端点击路径。
- 历史存量数据只能用于“可见性/复核既有记录”场景；SA-012/SA-013/SA-014 这类“用户触发并完成动作”的黄金路径，必须从页面完成关键动作。
- 自动化测试如果需要已知起点，必须在同一个 serial Playwright 旅程中通过 UI 建立，或明确声明它验证的是外部 fixture/存量可见性，而不是完整业务用户旅程。

可执行入口：

- 本地前后端统一托管入口：`scripts/dev/start-local-env.sh hold`
- 本地前后端状态检查/停止入口：`scripts/dev/start-local-env.sh status` / `scripts/dev/start-local-env.sh stop`
- 后端/真实 GitLab 场景验收入口：`bash scripts/acceptance/run-acceptance.sh`
- Slice-1 分组/发布窗口旅程：`cd frontend && pnpm run test:e2e:slice-1`
- 完整前端用户旅程基线：`cd frontend && pnpm run test:e2e`
- 测试源码入口：`frontend/e2e/tests/slice-1-group-window.spec.ts`、`frontend/e2e/tests/slice-2-full-flow.spec.ts`

---

## 2. 已完成事项

2026-11-21：S3-A/B1 与 S3-B2 已验收并推送。Attach/Detach/Publish 不写 Git；Orchestrate 封存权威范围，MR-first、原 revision 与数字 project ID 贯穿真实写出口，UNKNOWN 保留仓库 guard。Run 对外状态统一为 PLANNED/RUNNING/DONE/PARTIAL_FAILED/FAILED/NEEDS_REVIEW，重试使用实际 Item ID。

B2 close 在窗口行锁保护的短事务中一起提交 CLOSED、WINDOW_CLEANUP Run 和不可变计划，之后才执行 Git。按 `(window, repository)` 生成 Item，`iterationKey=null`，`participatingIterationKeys` 保存真实参与者；重复或并发相同选择返回原 Run，改变 `triggerCi` 返回冲突。在途 RUNNING/NEEDS_REVIEW 编排阻止首次收尾，PLANNED 预览不阻止；生命周期写入和编排受理共用窗口锁。

收尾确认真实 release/MR 合并证据后，Tag 指向准确 mergeSha；`triggerCi` 默认 false，显式开启才使用计划 Tag/ref 触发 CI，实际请求与响应以执行后确认的 MR mergeSha 校验，失败或 UNKNOWN 不派发后继写。feature/release 和共享 Iteration 保留，`cleanup.referencePolicy=RETAIN` 明确记录；不自动归档、删除或关闭 Iteration。API/UI/报告已迁移仓库级身份，普通 cleanup retry 继续禁止。V38 放宽仓库级 Item 的 iteration_key 非空约束；普通集成三元组仍由领域约束，不补造历史执行事实。

B2 前的 A/B1 历史验收基线保留：普通后端 486 PASS/5 既有 SKIP、数据库/API 53 PASS、前端 132 PASS；提交前独立 Git 基础树 194 PASS，另有 H2/PostgreSQL V36→V37 混合数据迁移及 OSIV=false 读取验证。该旧提交分组不代表后续 S3-C1/C2 恢复能力已实现。

2026-10-08：S2-A 至 S2-D 实施收口。V35 无历史回填；未知结果和业务回滚保留不随时间或核对成功解除，受控模式下 GitHub 写、自动删除和本地文件回退拒绝。专用测试库保留 1 条未结束 guard 作为真实负向证据，未清库绕过。详见 S2 实施记录。

2026-10-08：代码已随 `5d85b17` 推送，S2 范围评审随后完成。发现临时 MR/PR 写入、归档失败仍删除、独立文件路径和错误折叠等前提缺口；具体方案见 `docs/openspec/changes/add-durable-run-recovery/s2-review.md`。评审完成不等于产品实现或外部能力验收。

2026-10-08：S1 新 Run/Item 使用独立 UUID，旧 ID 与空协议字段读取兼容；计划预览具备同主体幂等键、不可变快照、API/UI、PLANNED 筛选及旧 retry 阻断。V34 无历史回填；Flyway 与 local 自建 schema 在隔离测试中验证通过。后续 SA-018 已修复真实类型门禁。

2026-10-05：SA-013 设计阶段完成。确认分支已创建而 Run 未保存、未知结果可重放、保存失败前已有模拟外部效果、标识碰撞和执行项跨 Run 转移；形成完整设计、11 个 Requirement / 27 个 Scenario 和 S1–S5 追踪。仅设计与证据完成，恢复尚未实现。

2026-10-05：SA-018 已完成。项目声明 pnpm 精确版本，CI/Docker 读取同一来源，Node 使用 24 主版本；三个工作流补齐 develop 推送，95 项单测、6 项 Pact 和生产构建通过。详见 `tasks/records/2026-10-05-sa-018-build-ci-consistency.md`。

2026-10-05：SA-004 生产默认启用 AES-GCM 和只读存量密文校验，仓库与 Settings Token 在重启、SQL 备份恢复后仍可用于连接；无标识旧凭据未迁移，密钥未轮换。

2026-10-05：SA-017 已完成。生产空用户表使用显式凭据创建管理员，支持并发保护和重复启动；已有账户不改动。修复 Compose PostgreSQL 18.1 挂载路径，验证重建后数据保留。

以下功能表保留带日期的历史证据；历史 GitLab/Playwright 通过只代表当时实现。涉及当前 Attach、Run 状态和收尾的事实以 2026-11-21 条目为准，不能用历史自动归档、CI_NOT_CONFIGURED 或旧状态名证明 B2 行为。

| 事项 | 状态 | 证据路径 | 验证方式 | 备注 |
|---|---|---|---|---|
| ReleaseWindow CRUD + 状态流转 | 已实现 | `releasehub-application/...releasewindow` | 单测 + acc-v0.1.10 #7,12,17 | 闭环 |
| SA-008 多窗口并行发布可观测性 | 已验证 | `ReleaseWindowAppService.getParallelScope` + `ReleaseWindowDetail.vue` | MockMvc + Vitest | 同组活跃窗口可按 `windowId/windowKey` 追溯迭代、仓库和发布计划；列表、日历、详情均展示并行窗口线索 |
| Iteration CRUD | 已实现 | `IterationAppService` | 单测 21 用例 | 闭环 |
| Iteration 三层关联 + BranchCreationMode | 已实现 | `BranchCreationMode.java` + V28 + IterationAppService.switch | 单测 + acc-v0.1.11 #10.1-10.5 | 闭环 |
| SA-009 同分组仓库选择与写入保护 | 已验证 | `IterationAppService.ensureReposBelongToGroup` + `AddReposDialog.vue` | 应用层单测 + Vitest + typecheck/i18n + 静态扫描 | 创建、更新和追加仓库写入前拒绝跨分组仓库；前端添加仓库弹窗只展示同分组候选 |
| SA-009 已挂窗口后迭代仓库集合锁定 | 已验证 | `IterationAppService.ensureRepoScopeEditable` + `IterationDetail.vue` | 应用层单测 + interfaces compile + Vitest + typecheck/i18n + 静态扫描 | 已挂窗口后拒绝 add/remove/update 改仓库集合或分组；详情页隐藏添加/移除入口 |
| SA-009 移除仓库保留远端分支 | 当前代码/单测通过，真实 fixture 脚本待重跑 | `scripts/acceptance/sa009-remove-repo-gitlab-evidence.sh` + `IterationAppService.removeRepos` | 当前 37 项迭代测试与脚本语法检查；历史归档证据不代表当前行为 | 未挂窗移除仅解除关联和关联版本配置，保留原 feature 与历史 Run；已挂窗移除继续拒绝。脚本已断言原 SHA 不变且不产生归档分支，本轮未执行远端验证 |
| SA-009 大规模迭代仓库可观测性 | 已验证 | `IterationAppService.listRepoDetailsPaged` + `IterationDetail.vue` | 应用层单测 + MockMvc + Vitest + typecheck/i18n + 静态扫描 | 单个迭代关联较多仓库时，服务端分页返回当前页仓库、分支模式和版本记录；详情页展示分页摘要和分页控件 |
| GitBranch 多 Provider 端口（Mock/GitHub/GitLab） | 已实现 | `GitBranchAdapterFactoryImpl` + 三 Adapter | 单测 + acc-v0.1.11 真实 API 调用 | GitLab 通过 `@ConditionalOnProperty(real-adapter=true)`；GitHub Adapter 默认装配（架构不对称） |
| Token AES-GCM 加密 | 已实现 | `GitTokenCrypto` + `GitTokenAttributeConverter` + `run-acceptance.sh` SA-002 | 10 单测 + 验收脚本审计 | 闭环；新密文使用 `enc:v1:` 前缀，兼容历史无前缀密文；仓库 token 和 GitLab Settings token 均纳入明文审计；新 DB 数据为空时 加密=0 是正常 |
| SA-002 存量清理人工复核入口 | 已验证 | `DataQualityCleanupReviewAppService` + `DataQualityCleanupController` + `sa002-safe-cleanup.sh` | 应用层单测 + MockMvc + dry-run 报告 | dry-run 动作保留应用入口、执行前检查、执行后复核和人工复核决策；复核入口只允许进入应用层处理，不执行清理；越权执行、不完整动作和未知风险均拒绝 |
| SA-002 验收脚本与应用 API 数据源口径统一 | 已验证 | `CleanupReviewResult.assetBoundaries` + `DataQualityReviewQueue.vue` + `run-acceptance.sh` + `sa002-safe-cleanup.sh` | 应用层/API/前端专项测试 + 浏览器冒烟 + 静态扫描 | 全量验收、safe-cleanup 报告和复核队列统一使用 `API_VISIBLE_ASSETS`、`DB_AUDIT_ASSETS`、`REVIEW_QUEUE_ACTIONS`，避免把数据库审计总量误读为用户可见风险 |
| SA-002 数据质量人工复核处置策略 | 已验证 | `DataQualityCleanupReviewAppService` + `CleanupActionReview` + `DataQualityReviewQueue.vue` | 应用层/API/前端专项测试 + typecheck/i18n + 静态扫描 | 复核结果返回处置等级、允许动作、失败回滚边界和审计记录口径；应用层人工处置、独立迁移服务和只读观察三类风险边界可见；所有结果仍 `executionPermitted=false` |
| SA-002 数据质量受控处置执行审计设计 | 已验证 | `docs/openspec/changes/update-data-quality-disposition-audit/` + `docs/requirements/in-progress/SA-002-数据质量受控处置执行审计.md` | 需求/OpenSpec/路线图检查 + typecheck/i18n | 已定义处置 case、状态机、幂等键、脱敏快照、失败恢复和处置等级边界；最小实现仍不得直接修改业务资源 |
| SA-002 数据质量受控处置 case 最小实现 | 已验证 | `DataQualityDispositionCaseAppService` + `DataQualityDispositionCaseController` + `DataQualityReviewQueue.vue` | 应用层/API/前端专项测试 + typecheck/i18n + 静态扫描 | 支持处置 case create/list/detail/start/verify/fail/cancel；前端可创建、查看和记录状态；所有接口只更新审计记录，不修改业务资源 |
| SA-002 BranchCreationMode 独立迁移服务设计 | 已验证 | `docs/openspec/changes/add-branch-creation-mode-migration-service/` + `docs/requirements/in-progress/SA-002-BranchCreationMode独立迁移服务设计.md` | 需求/OpenSpec/路线图检查 + typecheck/i18n + 静态扫描 | 已定义迁移对象、候选分类、dry-run、执行计划、受控执行、复核和回滚边界；当前不实现执行器、不写数据库、不触碰 GitLab；静态扫描 `.ai/reports/static-scan/20260524-160003/summary.md` |
| SA-002 BranchCreationMode 迁移服务 proposal 评审门禁 | 已验证 | `docs/openspec/changes/add-branch-creation-mode-migration-service/review.md` + `tasks/records/2026-05-24-sa-002-branch-mode-migration-proposal-review.md` | proposal 评审 + roadmap 检查 + typecheck/i18n + 静态扫描 | 结论为 `APPROVE_DRY_RUN_ONLY`；只批准最小 dry-run，不批准执行 API、数据库写入、迁移审计表、前端执行入口或 GitLab 操作；静态扫描 `.ai/reports/static-scan/20260524-160645/summary.md` |
| SA-002 BranchCreationMode 最小 dry-run 实现 | 已验证 | `BranchCreationModeMigrationDryRunAppService` + `POST /api/v1/data-quality/branch-creation-mode-migrations/dry-run` | 应用层/API 测试 + roadmap 检查 + typecheck/i18n + 静态扫描 | dry-run 输出四类候选、统计、JSON 结构和 Markdown 报告，且 `executionPermitted=false`；不写数据库、不触碰 GitLab；静态扫描 `.ai/reports/static-scan/20260524-162409/summary.md` |
| 本地开发库可重复清理脚本 | 已验证 | `scripts/dev/cleanup-dev-database.sh` + `docs/context/tech/conventions/database.md` | 脚本语法检查 + 本地 dry-run 报告 + 静态扫描 | 本地开发阶段可随时 dry-run 或显式 `--execute` 清理业务表；默认限定 `releasehub-postgres/release_hub`，覆盖 `release_hub` 与历史 `public` schema，保留 Flyway、用户和系统设置；dry-run 识别 27 张候选表、5382 行候选数据；静态扫描 `.ai/reports/static-scan/20260524-194230/summary.md` |
| Attach/Detach 配置边界 | 已验证并推送 | `AttachAppService` + `WindowIterationPort` | 8 项独立目标单测 | 不写 Git、不新建执行 Run；旧独立写路由已删除 |
| 冲突检测（7 种）| 已实现 | `ConflictDetectionAppService` | acc-v0.1.10 #11 + acc-v0.1.11 #5 | 闭环 |
| 远程版本更新 | 已验证 | commit `bbbae46` + `MavenVersionUpdaterAdapter` + `GradleVersionUpdaterAdapter` + `run-acceptance.sh` SA-014 | 单测 + 真实 GitLab 验收 | Maven 单模块、多模块和 Gradle release 分支 commit 已验证 |
| 设置持久化（GitLab settings） | 已验证 | commit `55cb5b0` + `SystemSettingsJpaEntity` + `run-acceptance.sh` SA-001/SA-004 | 单测 + 后端重启验收 + token 加密审计 | 重启后 settings 和仓库数据可查询；`system_settings.gitlab_token` 经 JPA converter 透明加密 |
| GitLabRequest 反序列化修复 | 已实现 | commit `962be80`（@NoArgs/@AllArgs） | 单测 + acc-v0.1.11 第 1 轮 POST /settings/gitlab 成功 | 闭环 |
| SA-004 GitLab Settings 连接测试 | 已实现 | `GitLabPort.testConnection` + `GitLabAdapterTest` + `Settings.vue` | MockMvc + WireMock + Vitest + i18n | `/settings/gitlab/test` 调用真实 GitLab `/api/v4/user`，前端成功/失败提示闭环 |
| SA-004 GitLab 连接异常诊断展示 | 已验证 | `GitLabAdapter.testConnection` + `Settings.vue` | WireMock + MockMvc + Vitest + i18n | token 无效、权限不足和 GitLab 不可达分别返回 `GITLAB_004/005/006`；页面内展示安全诊断文案，不泄露 token/baseUrl/原始响应体 |
| SA-006 历史不合规分支治理入口 | 已验证 | `BranchGovernanceAppService` + `RepositoryDrawer.vue` + `RepositoryDetail.vue` | 应用层单测 + MockMvc + Vitest + GitLab adapter 单测 | 仓库详情可见活跃历史不合规分支和人工治理边界；`archive/...`、默认分支和基础分支不会进入治理清单；本切片不自动重命名、删除或归档 |
| SA-013 发布编排 Run 页面内复核 | 已验证 | `OrchestrationPanel.vue` + `RunJpaPersistenceAdapter` | MockMvc + Vitest + typecheck/i18n + 静态扫描 | 窗口详情触发编排后展示最新 Run ID、状态、执行项和失败项数量；失败 Run 展示发布窗口/仓库/迭代上下文、失败步骤和失败原因；Run 分页按开始时间倒序返回 |
| SA-005 仓库详情组织路径和版本状态 | 已实现 | `InitialVersionView.versionSource` + `RepositoryDrawer.vue` + `RepositoryDetail.vue` | MockMvc + Vitest + i18n/typecheck | 仓库详情可展示分组树路径、初始版本和版本来源；Settings 清空语义同步修正为空配置 |
| SA-005 Clone URL 纳管保护 | 已实现 | `CloneUrl` + `CodeRepositoryAppService.ensureCloneUrlUnique` + `RepositoryEdit.vue` | 领域/应用单测 + 前端 Vitest | 创建/更新仓库前拒绝不可解析 Git 地址；SSH/HTTP(S)/`.git`/大小写差异指向同一仓库时拒绝重复纳管 |
| SA-005 仓库 Mock Provider 持久化边界 | 已验证 | `CodeRepositoryAppService.ensureProductGitProvider` + `RepositoryEdit.vue` + `sa002-safe-cleanup.sh` | 应用层/API/前端门禁 + 静态扫描 | 产品仓库创建/更新拒绝 `gitProvider=MOCK`；仓库表单不展示 Mock；Slice-2 UI journey 使用真实 GitLab fixture；历史 Mock Provider 仅作为 SA-002 dry-run 风险复核；静态扫描 `.ai/reports/static-scan/20260524-192807/summary.md` |
| SA-005 版本解析失败修复引导 | 已实现 | `repositoryApi.syncInitialVersion` + `RepositoryDetail.vue` | Vitest + typecheck/i18n | 详情页在初始版本为空或 `VERSION_UNRESOLVED` 时提供重新解析入口，成功后页面内刷新版本号和来源 |
| SA-005 仓库组织筛选 | 已实现 | `CodeRepositoryAppService.searchPaged(keyword, groupCode, ...)` + `RepositoryList.vue` | 应用单测 + E2E + Vitest + typecheck/i18n | 仓库列表可选择任意组织节点筛选；选择父组织时包含所有子组织仓库 |
| SA-005 删除保护扩展 | 已实现 | `CodeRepositoryAppService.delete` + `GroupAppService.deleteByCode/deleteById` + `deleteProtectionMessageKey` | 应用层单测 + Vitest + typecheck/i18n | 仓库被迭代引用时拒绝删除；分组有子分组或被仓库/迭代/发布窗口引用时拒绝删除；前端展示明确阻断提示 |
| Publish 配置边界 | 已验证 | `ReleaseWindowAppService.publish` | 零 Git/零 Run 目标单测及全后端回归 | 已删除自动编排 listener/event；发布仅改变生命周期，集成由显式 Orchestrate 触发 |
| 验收脚本 v3.2（含 ensure-settings + token 刷新 + 冲突识别） | 已实现 | commit `9eb5444` + `68381b1` + 本会话 | acc-v0.1.11 第 3 轮 25/26 PASS | 闭环 |
| GitLabGitBranchAdapter URL 双重 encode 修复 | 已实现 | 本会话（uri(...) 包装 + ENC 测试同步） | acc-v0.1.11 终轮 release 分支 3/3、listBranches 18 个 | 闭环 |
| 场景化验收矩阵基线 | 已验证 | `docs/reports/scenario-acceptance-matrix.md` + `scripts/acceptance/run-acceptance.sh` | 2026-05-21 真实 GitLab 证据复核 | PASS=159 / FAIL=0 / SKIP=0；SA-010 detach 后 release 分支归档真实 GitLab 强证据已补；SA-011 六类风险后端/GitLab 强证据已补；SA-014 Maven 多模块/Gradle 真实写回和批量多仓部分失败证据已补；SA-015/SA-016 真实部分失败重试强证据已补 |
| SA-011 版本领先风险前端观察 | 已验证 | `frontend/e2e/tests/slice-2-full-flow.spec.ts` | 2026-05-18 Slice-2 Playwright | `REPO_AHEAD` / `SYSTEM_AHEAD` 类型分布、版本差异、阻断级别、建议处理方式和 `USE_SYSTEM` 同步请求语义已覆盖 |
| SA-011 Git 访问异常风险闭环 | 已验证 | `ConflictType` + `ConflictDetectionAppService` + `IterationAppService` + `ConflictPanel.vue` + `run-acceptance.sh` 5.9 + Slice-2 Playwright | 后端单测 + adapter 单测 + Vitest + typecheck/i18n + 真实 GitLab 证据复核 + 外部 Playwright 页面旅程 | 新增 `GIT_PERMISSION_DENIED` / `GIT_UNAVAILABLE`，避免权限/不可达被吞掉、冒泡或误判为普通合并冲突；迭代仓库 setup 遇到外部 Git I/O 异常时不再 500，后续冲突扫描保留可追踪证据；真实 GitLab 权限不足/不可达和前端用户旅程证据已补 |
| 前端 SA-012/SA-013/SA-014 用户触发旅程 | 已验证 | `frontend/e2e/tests/slice-2-full-flow.spec.ts` | 2026-05-14/15 真实前后端联调 + 前端请求证据 | UI 创建业务数据后完成版本冲突解决、分支名不合规和 release 分支已存在外部处理观察、编排和版本更新触发；最终请求体断言作用域正确 |
| 前端 Playwright E2E 基线 | 已验证 | `frontend/e2e/tests` | 2026-05-15 真实前后端联调 | 29 PASS / 0 FAIL / 0 SKIP；历史显式 skip 已转为可执行旅程 |
| SA-003 资源创建叶子分组前端断言 | 已验证 | `frontend/e2e/tests/slice-1-group-window.spec.ts` | 2026-05-20 Slice-1 Playwright | UI 创建三层分组后，仓库、迭代、发布窗口创建弹窗均断言非叶子分组 `aria-disabled=true`，只能选择叶子分组 |
| SA-003 受控空叶子分组移动 | 已验证 | `GroupAppService.update` + `GroupDialog.vue` | 应用层单测 + Vitest | 分组父级变更被视为组织移动：有子分组、已被仓库/迭代/发布窗口引用的分组不得移动；已挂资源的目标父分组不得成为新上级；前端编辑弹窗展示明确移动阻断提示 |
| SA-003 分组父级树选择体验 | 已验证 | `GroupTreeSelect.vue` + `GroupDialog.vue` + `frontend/e2e/tests/slice-1-group-window.spec.ts` | Vitest + Slice-1 Playwright | 分组编辑弹窗使用组织树选择父分组，清空表示顶层分组，当前分组自身禁选；外部 Playwright 已复核空叶子分组通过父级树选择器移动成功 |
| SA-003 code 自动生成当前证据 | 已验证 | `GroupAppServiceValidationTest` + `frontend/e2e/tests/slice-1-group-window.spec.ts` | 应用层单测 + Slice-1 Playwright | code 缺省或空白时，顶层分组按三位数字递增，子分组继承父 code 并追加三位序号；页面创建顶层/子分组留空 code 后可在组织树复核生成值 |
| 本地环境统一启停脚本 | 已验证 | `scripts/dev/start-local-env.sh` | 2026-05-15 真实前后端联调 | `start|hold|stop|restart|status` 可用；`hold` 托管前后端；前端 `/api` 代理登录 200 |
| 开源单机源码部署包 | 已实现 | `deploy/compose/docker-compose.yml` + `backend/Dockerfile.prod` + `frontend/Dockerfile.prod` + `docs/deploy/docker-compose.md` | compose 配置校验 + 脚本语法检查 | 面向开源用户的 canonical 部署入口：单台 Docker 主机源码构建前后端，内置 PostgreSQL，外部 GitLab 通过 Settings 配置；提供备份、恢复、升级和健康检查脚本 |
| SA-016 仓库级安全收尾（2026-11-21） | B2 已验收并推送 | `RunPlanAppService.admitCleanup`、`RunAppService`、`RunPlanApiIT` | 真实事务/PostgreSQL/API、本地 HTTP fixture、组件测试 | 同仓一次受理；原 Run 幂等回执；精确 Tag、显式 CI；生命周期锁及在途编排屏障；普通 cleanup retry 禁止 |
| SA-016 发布后收尾历史闭环 | 历史验证（2026-05-15） | `scripts/acceptance/run-acceptance.sh` + `frontend/e2e/tests/slice-1-group-window.spec.ts` | 当时的真实 GitLab 证据复核 + 外部 Playwright | 保留关闭、重复关闭和页面可见性证据；不作为 B2 仓库级 receipt、Tag/CI 或共享引用保护的本轮验收 |
| SA-016 发布窗口报告导出 | 已实现 | `ReleaseWindowReportController` + `ExportAppService` + `ReleaseWindowDetail.vue` | MockMvc + Vitest + typecheck/i18n | 窗口维度 JSON/CSV 汇总 window、Run、RunItem、RunStep、结果分布；详情页可导出 CSV 报告 |
| SA-016 发布报告制品包归档 | 已实现 | `ReleaseWindowReportController` + `ReleaseWindowDetail.vue` | MockMvc + Vitest | 发布窗口可下载 `release-window-<windowKey>-evidence.zip`，包内固定包含 manifest、JSON、CSV、Markdown 四个证据文件；详情页导出菜单提供制品包入口 |
| SA-016 显式 CI 及精确证据 | B2 已验证 | `GitBranchPort`、`GitLabGitBranchAdapter`、`RunAppService.executeCleanup` | typed adapter 单测 + HTTP/PG 组合测试 + API/组件 | `triggerCi=false` 默认 SKIPPED；显式 true 要求稳定 Tag，成功核实 project/id/ref/SHA/url；CI_TRIGGERED 只表示已触发，UNKNOWN 保留 guard |
| SA-016 关闭后 GitLab 收尾历史证据 | 历史验证（2026-05-23） | 当时的 `run-acceptance.sh` / 任务记录 | 当时的真实 GitLab 验收 `169 PASS / 0 FAIL / 0 SKIP` | 旧归档和按迭代收尾语义仅作历史；B2 保留 feature/release 与 Iteration，本轮未重跑真实 GitLab |
| SA-010 解除挂载外部 Playwright 页面复核 | 已验证 | `frontend/e2e/tests/slice-1-group-window.spec.ts` | 2026-05-19 Slice-1 Playwright | UI 创建迭代、挂载到窗口、详情页解除挂载、`detach` 请求成功、关联列表为空和发布计划隐藏 |
| SA-010 解除挂载 GitLab 分支归档历史证据 | 历史验证（2026-05-20） | 当时的 `run-acceptance.sh` 4.2 | 当时的真实 GitLab 证据复核 | 旧 detach 归档行为已经删除；当前 Attach/Detach 只维护绑定，不创建、归档或删除分支 |
| SA-010 同分组挂载范围约束 | 已验证 | `AttachAppService` + `AttachIterationsDialog.vue` | 后端单测 + API/组件 + 静态扫描 | 跨分组迭代完整预校验后拒绝，零绑定写入；配置操作零 Git、零执行 Run；前端禁选非同分组行 |
| SA-010 发布后发布计划变更锁定 | 已验证 | `AttachAppService` + `ReleaseWindowDetail.vue` | 后端单测 + Vitest | PUBLISHED/CLOSED 窗口 attach/detach 均被 `RW_009` 拒绝；详情页发布后隐藏挂载和解除挂载入口 |
| SA-012 REPO_AHEAD 接受仓库版本 | 已验证 | `IterationAppService.resolveVersionConflict` + `ConflictPanel.vue` + `ReleaseWindowDetail.vue` | 后端单测 + Vitest + typecheck/i18n | 仓库版本较新可提交 `USE_REPO`，后端更新系统版本并标记来源 `REPO`，前端显示“接受仓库版本” |
| SA-014 批量版本更新前端入口 | 已验证 | `VersionUpdateDialog.vue` + `releaseWindowApi` | Vitest + typecheck/i18n | 多仓窗口可切换到批量范围，按窗口关联仓库提交既有批量版本更新端点 |
| SA-014 Maven 多模块 / Gradle 真实写回 | 已验证 | `scripts/acceptance/run-acceptance.sh` 8.3/8.4 + `MavenVersionUpdaterTest`/`GradleVersionUpdaterTest` | 真实 GitLab 证据复核 + 单测 | Maven 多模块 root/module POM 写回 `2.3.0`，Gradle `gradle.properties` 写回 `3.2.0`，均有 release 分支 commit 证据 |
| SA-014 批量版本更新多仓部分失败 | 已验证 | `scripts/acceptance/run-acceptance.sh` 8.5 | 真实 GitLab 证据复核 | 批量 Run 同时包含 `VERSION_UPDATE_SUCCESS` 和 `VERSION_UPDATE_FAILED`，失败原因包含缺失 POM 路径，成功仓库 release 分支真实写回 `1.6.0` |
| SA-014 版本更新失败重试 | 历史验证，当前入口不支持 | 历史 `RunAppService.retry` 与组件证据；当前 `isRetryableRun` / retry 协议校验 | 历史测试不能代替当前契约 | 当前共享 retry 仅接受 WINDOW_ORCHESTRATION v3；VERSION_UPDATE 页面无重试入口，API 返回 RUN_007/409。版本动作的安全重试与恢复在 S4 验收 |
| Maven surefire/failsafe 插件版本显式化 | 已验证 | `backend/pom.xml` | `mvn -pl releasehub-bootstrap -DskipTests validate` + `mvn -pl releasehub-application -Dtest=ConflictDetectionAppServiceTest test` | malformed POM 中插件版本缺失警告已关闭 |
| acc-v0.1.10 验收报告归档 | 已完成 | `docs/reports/archive/acceptance-v0.1.10-real-gitlab.md` | 文档引用复核 | v0.1.10 已被 v0.1.11 和场景矩阵覆盖，顶层 reports 目录瘦身 |
| SA-016 种子分支清理执行保护 | 已验证 | `scripts/e2e/reset-gitlab-seed-branches.sh` | dry-run + execute + repeat execute 真实 GitLab 证据 | 默认 dry-run 输出候选清单；`--execute` 删除 799 个非种子分支并确认移除，7 个 main/seed feature 分支保留；复跑删除 0 个分支 |

---

## 3. 已验证事项

### 2026-11-21 C2-B1 最终验证与交付

| 验证边界 | 结果 | 证据与限制 |
|---|---|---|
| 最终组合复验 | SQL 与构造器整理后再次 76/76：PG 31、HTTP 33、独立进程 2、worker 7、scope 3 | `.ai/reports/validation/c2-b1/static-fix-green/result.json`，backend hash 前后一致；重叠 suite 不累计 |
| 静态整理前 API 与普通回归 | API 48/48；普通后端 680 项：675 PASS、5 既有 SKIP | `.ai/reports/validation/c2-b1/final-backend/result.json`，各轮 hash 稳定；不称为最终 SQL 整理后的同次运行，不将旧前端 214 项记为本片新验收 |
| 独立 JVM 硬杀 | PENDING、CLAIMED_UNWRITTEN 均退出 137，MR POST=1、MERGE PUT=1；原 Item、Task 和成功 Step 保持 | 最终 76 项组合包含 2 个进程用例；只证明原 PENDING 继续与无 operation 的过期 RUNNING 接管，不证明已有写意图恢复 |
| Schema 与时间 | V40 可空 claim_expires_at 与 JPA 映射；默认 lease 60 分钟、无心跳，NULL 不回填 | local 用例仅验证新增列，未验收完整 local 业务链；首次时间和已有实际顺序保留，缺顺序不补造 |
| 静态与交付 | 最终 6 份新鲜 SpotBugs XML 均为 0，typecheck PASS、lint 0 errors/8 既有 fixture warnings；后端 `3f810c8`、文档 `5441df6` 已推送 | `.ai/reports/static-scan/20261121-214959/summary.md` 逐项处置告警；首扫 raw 的 4 项及摘要纠正保留在 `20261121-213959` |
| 本片固定 SHA 真实 CI | `5441df6bdc971953dee390c9470d4786fe0eab44`；run 44911634004 / job 136868521085，SUCCESS；日志 2026-11-21T14:09:44.5034671Z（北京时间22:09:44）为 Passed 5 / Failed 0 | [Full-Link 44911634004](https://github.com/tongsh6/release-hub/actions/runs/44911634004)；`.ai/reports/validation/c2-b1/full-link/result.json`、`136868521085.log` |

B1 完成时曾将唯一 HEAD 推进 C2-B2；当前任务以路线图为准。该 CI 是后端 API＋隔离 GitLab；Slice4/5 既有覆盖限制与完整 UI 黄金旅程未验收边界保留，不扩写为已有写意图的自动恢复。

### 2026-11-21 C2-A 最终验证与交付

| 验证边界 | 结果 | 证据 |
|---|---|---|
| 本地代码与故障验证 | 普通后端 677 项（672 PASS/5 既有 SKIP）；API/PG 67/67、真实 HTTP 29/29、独立 JVM 1/1、后补 worker 7/7；重叠 suite 不累计 | 本轮研发记录第 13 节 |
| 前端与消费者 | 39 文件 214/214，typecheck/i18n/lint/build 通过；E2E fixture 契约最后补验 16/16，三脚本语法及 diff-check 通过 | `.ai/reports/validation/c2a-final-*.log`、`.ai/reports/validation/c2-a-e2e-helper/green/` |
| 静态门禁 | SpotBugs 0、typecheck PASS、lint 0 errors/8 既有 warnings，TopN 与 EOF 复查通过 | `.ai/reports/static-scan/20261121-203554/summary.md` |
| 本次固定 SHA 真实 CI | `1761572bca6c149aecba78d26a7b25cb98b5251a`，run 44905843754 / job 136851120556；2026-11-21T12:59:28Z 日志明确 Passed 5 / Failed 0 | [Full-Link 44905843754](https://github.com/tongsh6/release-hub/actions/runs/44905843754)、`.ai/reports/validation/c2-a-full-link/` |

C2-A 已 DONE。CI 是后端 API＋隔离 GitLab；Slice3 保留 202 原 Run/Task、DONE、身份和远端计数断言，Slice4/5 的既有覆盖限制仍在，完整 UI 黄金旅程未因此通过。C2-A 阶段不接管 RUNNING；B1 的限定接管已取得独立本地及固定 `5441df6` 的 5/5 证据并完成交付，不把 C2-A 的 PENDING 自动继续扩写为任意中断恢复。

### 2026-11-21 C1 本地候选验证

| 验证边界 | 结果 | 证据 |
|---|---|---|
| 普通后端回归 | 57 类 657 项：652 PASS、5 既有 SKIP；后续范围增量由最后契约组覆盖 | `.ai/reports/validation/c1-backend-candidate-final.json` |
| 最后候选契约 | 9 类 148/148：Iteration 37 + bootstrap/API/真实 PG/HTTP/fixture/独立进程 111；运行期间 backend 源码 hash 不变 | `.ai/reports/validation/c1-final-contracts.json` |
| 前端 | 38 文件 170/170，typecheck/i18n/lint/build 通过；纯配置文案后 3 文件 6 项及 i18n 复验通过 | `.ai/reports/validation/c1-frontend-final-*.log`、`c1-iteration-config-ui.log` |
| 实际进程中断 | 独立 JVM 在 ENSURE_MR 已提交后强杀；重启同 actor/key 返回原 Run，新 HTTP=0，TRY 保持 PENDING | `RunTaskProcessKillIT` 与 `.ai/reports/validation/c1-final-contracts-xml/` |
| 静态扫描与文档 | SpotBugs 0、typecheck PASS、lint 0 errors/8 既有 warnings；TopN 逐项处置，EOF 修正后 diff-check 0；OpenSpec 2.1.1 strict 通过 | `.ai/reports/static-scan/20261121-175548/summary.md`、`c1-openspec-strict-final.json` |
| 真实 CI / 页面旅程 | 固定 e5094ba 的 Full-Link 44899945002：5 切片 PASS、0 FAIL，Slice3 真实首次编排与 close DONE；当前完整浏览器黄金旅程未执行 | 本轮任务记录第 10 节及 `.ai/reports/validation/c1-full-link-44899945002-status.json` |

定向与普通回归的重叠用例不累计。配置入口已删除隐式建分支/归档，EXISTING 保留只读校验；repoConfigs 不扩大 repoIds、不接受重复，未配置的新仓默认 AUTO。C1 保证可追溯且安全停住，自动继续属于 C2。五切片的现有覆盖强度不同，不能把 Slice4 的步骤结构或 Slice5 的配置断言当作完整真实收尾/合并冲突验收。

### 2026-11-21 B2 最终验证

| 验证边界 | 结果 | 证据 |
|---|---|---|
| 普通后端回归 | 54 类 613 项：608 PASS、5 既有 SKIP，0 FAIL/ERROR | `.ai/reports/validation/b2-backend-final.log` |
| 六类数据库/API/本地 HTTP fixture | 70/70 PASS；包括 H2/PostgreSQL V37/V38、真实事务受理/并发、严格 HTTP 写入证据 | `.ai/reports/validation/b2-api-final.log` |
| 前端组件与类型/构建 | 38 文件 149/149；typecheck、i18n:lint、lint、build 通过 | `.ai/reports/validation/b2-frontend-final.log` |
| OpenAPI 与生成类型 | springdoc 2.8.14 实际 API 200；OpenAPI 3.1 可空字段、可选 close body 和 Run 响应已断言；99 paths/171 schemas 同步真实消费者 | `.ai/reports/validation/b2-openapi-green.log` |
| 最终静态扫描 | SpotBugs 0；lint 0 errors/8 个既有 fixture warning，新增 warning 已清零 | `.ai/reports/static-scan/20261121-153304/summary.md` |

E2E Java fixture 已按新契约迁移并通过测试编译，接受脚本只做语法验证；本轮未运行完整 Playwright、真实 GitLab 收尾、独立进程 kill/restart 或 worker/resume。各轮定向测试与全量结果不累加。

### 历史验证记录

2026-10-08 SA-018：真实类型检查覆盖应用与全部工具配置；Vue/配置故障注入阻断，清理后通过。107 项前端单测、消费端与提供端 Pact 各 6 项、生产构建、i18n 通过；静态扫描无错误、8 条既有 lint warning。记录及报告见 `tasks/records/2026-10-08-sa-018-real-typecheck.md`。

| 事项 | 验证方式 | 报告路径 | 结论 |
|---|---|---|---|
| 场景矩阵全量复验 | `bash scripts/acceptance/run-acceptance.sh` | `docs/reports/scenario-acceptance-matrix.md` | 2026-05-21 **159 PASS / 0 FAIL / 0 SKIP**；数据资产 `323 groups / 120 repos / 293 windows / 465 iterations / 440 runs` |
| 全链路核心闭环 | 真实 GitLab 证据复核 v0.1.10 | `docs/reports/archive/acceptance-v0.1.10-real-gitlab.md` | 20/20 PASS |
| v0.1.11 全链路 + 三层关联 + 多 Provider + 设置持久化 | 真实 GitLab 证据复核 v0.1.11 终轮 | `docs/reports/acceptance-v0.1.11-real-gitlab.md` | **25 PASS / 0 FAIL / 1 SKIP**（SKIP 为业务正确拒绝） |
| SA-001 全量场景验收基线复跑 | `bash scripts/acceptance/run-acceptance.sh` + `bash scripts/dev/static-scan-topn.sh 10` | `tasks/records/2026-05-23-sa-001-full-baseline-rerun.md` | 全量场景验收 **170 PASS / 0 FAIL / 0 SKIP**；静态扫描通过，报告 `.ai/reports/static-scan/20260523-193829/summary.md`；下一缺口为 SA-002 脏数据报告与复核口径收敛 |
| SA-001 受控发布候选 dogfood/staging 验证 | 全量验收 + 前端 E2E 受控复跑 + 静态扫描 | `tasks/records/2026-05-24-sa-001-controlled-rc-validation.md` | 后端/真实 GitLab **170 PASS / 0 FAIL / 0 SKIP**；完整前端 E2E **49 PASS / 0 FAIL**；关键页面组件 **21 PASS / 0 FAIL**；静态扫描 `.ai/reports/static-scan/20260524-144034/summary.md`；路线图转向 SA-002 数据质量处置策略 |
| SA-002 验收脏数据报告与复核口径收敛 | `scripts/acceptance/sa002-safe-cleanup.sh --report-dir .ai/reports/sa002-safe-cleanup/20260523-aligned-baseline` + `bash scripts/dev/static-scan-topn.sh 10` | `tasks/records/2026-05-23-sa-002-cleanup-scope-alignment.md` | dry-run 已按后端 API 口径解释全量验收 DRAFT 告警，生成 **188** 条待复核动作：`DRAFT_WINDOW_REMAINS=187`、`ATTACH_BRANCH_NOT_CREATED=1`；同时输出 API 与数据库资产统计，`--execute` 继续拒绝；静态扫描报告 `.ai/reports/static-scan/20260523-194658/summary.md` |
| SA-001 发布候选收口报告与下一阶段路线图 | `bash scripts/dev/check-roadmap.sh` + `git diff --check` | `tasks/records/2026-05-23-sa-001-release-candidate-closeout.md` | 发布候选报告 `docs/reports/release-candidate-2026-05-23.md` 已形成；release-governance OpenSpec 已新增；当前分支可进入受控发布候选评审，路线图 HEAD 转向 SA-002 数据质量复核队列页面化 |
| SA-002 数据质量复核队列页面化 | 应用层/API/前端专项测试 + typecheck/i18n + 浏览器冒烟 + 静态扫描 | `tasks/records/2026-05-23-sa-002-data-quality-review-queue.md` | 数据质量复核页面已补：导入 `actions.jsonl`、按资源/风险/状态筛选、批量标记人工决策并提交受控复核；所有结果仍 `executionPermitted=false`；静态扫描报告 `.ai/reports/static-scan/20260523-200508/summary.md` |
| SA-001 发布候选评审页 / 发布经理检查清单 | 应用层/API/前端专项测试 + typecheck/i18n + 浏览器冒烟 + 静态扫描 | `tasks/records/2026-05-23-sa-001-release-candidate-review-page.md` | 发布候选评审页已补：聚合候选结论、验收证据、数据质量风险、残留非目标、检查清单和最近签核；签核只记录评审结论，不改变发布窗口、仓库、GitLab、数据质量清理或发布编排状态；静态扫描报告 `.ai/reports/static-scan/20260523-202310/summary.md` |
| SA-002 验收数据命名空间与保留策略 | 应用层/API/前端专项测试 + 脚本语法检查 + typecheck/i18n + 浏览器冒烟 + 静态扫描 | `tasks/records/2026-05-23-sa-002-acceptance-data-namespace-retention.md` | safe-cleanup 动作已补 `dataNamespace`、`reviewBatchId`、`assetScope`、`retentionPolicy`；复核 API 保留元数据并支持 `assetScopeFilter`；复核队列页面展示命名空间、批次、资产范围和保留策略；所有结果仍 `executionPermitted=false`；静态扫描报告 `.ai/reports/static-scan/20260523-203458/summary.md` |
| SA-002 验收脚本与应用 API 数据源口径统一 | 应用层/API/前端专项测试 + 脚本语法检查 + typecheck/i18n + 浏览器冒烟 + 静态扫描 | `tasks/records/2026-05-23-sa-002-data-source-boundary-alignment.md` | 全量验收、safe-cleanup 和数据质量复核队列统一 `API_VISIBLE_ASSETS`、`DB_AUDIT_ASSETS`、`REVIEW_QUEUE_ACTIONS`；复核 API 返回资产边界说明和资产范围计数；所有结果仍 `executionPermitted=false`；静态扫描报告 `.ai/reports/static-scan/20260523-204621/summary.md` |
| SA-002 数据质量人工复核处置策略 | 应用层/API/前端专项测试 + typecheck/i18n + roadmap 检查 + 静态扫描 | `tasks/records/2026-05-24-sa-002-cleanup-disposition-strategy.md` | 应用层 **9 PASS / 0 FAIL / 0 SKIP**；API **1 PASS / 0 FAIL / 0 SKIP**；前端 **2 PASS / 0 FAIL**；复核 API 和页面返回处置等级、允许动作、回滚边界和审计记录，仍不执行清理；静态扫描 `.ai/reports/static-scan/20260524-145408/summary.md` |
| SA-002 数据质量受控处置执行审计设计 | OpenSpec 手工校验 + typecheck/i18n + roadmap 检查 | `tasks/records/2026-05-24-sa-002-disposition-execution-audit-design.md` | 需求文档、OpenSpec proposal/design/tasks/delta spec 已形成；roadmap/typecheck/i18n/diff 检查通过；`openspec` CLI 本机不可用，未安装新工具 |
| SA-002 数据质量受控处置 case 最小实现 | 应用层/API/前端专项测试 + typecheck/i18n + roadmap 检查 + 静态扫描 | `tasks/records/2026-05-24-sa-002-disposition-case-minimal-implementation.md` | 应用层 **6 PASS / 0 FAIL / 0 SKIP**；API **2 PASS / 0 FAIL / 0 SKIP**；前端 **3 PASS / 0 FAIL**；处置 case 只更新审计状态，不执行清理；静态扫描 `.ai/reports/static-scan/20260524-152044/summary.md` |
| SA-002 数据质量处置 case 场景验收与证据归档 | 外部 Playwright 真实页面验收 + E2E 类型检查 + typecheck/i18n + roadmap 检查 + 静态扫描 | `tasks/records/2026-05-24-sa-002-disposition-case-ui-evidence.md` | 页面验收 **1 PASS / 0 FAIL**；组件 **3 PASS / 0 FAIL**；真实页面完成 dry-run 导入、提交复核、创建 case、查看详情、开始人工处置和记录复核通过；API 只作后置证据，不提供直接清理按钮；静态扫描 `.ai/reports/static-scan/20260524-154130/summary.md` |
| SA-001 发布候选交付证据收口与人工评审准备 | release-governance spec + 发布候选报告 + roadmap 检查 + 静态扫描 | `tasks/records/2026-05-24-sa-001-release-candidate-delivery-evidence.md` | 交付证据包已汇总 170 PASS 后端/真实 GitLab、49 PASS 前端 E2E、21 PASS 关键组件、SA-002 处置 case 页面验收和最新静态扫描 `.ai/reports/static-scan/20260524-155040/summary.md`；候选仍为 dogfood/staging，不声明 GA |
| SA-002 BranchCreationMode 独立迁移服务设计 | OpenSpec 手工结构校验 + roadmap 检查 + typecheck/i18n + 静态扫描 | `tasks/records/2026-05-24-sa-002-branch-creation-mode-migration-design.md` | 需求文档、proposal、design、tasks、data-quality/iteration delta spec 已形成；roadmap/typecheck/i18n/diff 检查通过；静态扫描 `.ai/reports/static-scan/20260524-160003/summary.md`；`openspec` CLI 本机不可用，未安装新工具 |
| SA-002 BranchCreationMode 最小 dry-run 实现 | 应用层/API 测试 + roadmap 检查 + typecheck/i18n + 静态扫描 | `tasks/records/2026-05-24-sa-002-branch-mode-migration-dry-run.md` | 应用层 **11 PASS / 0 FAIL / 0 SKIP**；API **2 PASS / 0 FAIL / 0 SKIP**；只读 dry-run 返回四类候选、统计、JSON 结构和 Markdown 报告，且 `executionPermitted=false`；静态扫描 `.ai/reports/static-scan/20260524-162409/summary.md` |
| 场景化验收矩阵基线复验 | `bash scripts/acceptance/run-acceptance.sh` | `docs/reports/scenario-acceptance-matrix.md` | **159 PASS / 0 FAIL / 0 SKIP**；新增 SA-014 批量版本更新多仓部分失败真实 GitLab 强证据 |
| SA-014 空仓库版本解析真实 GitLab 证据 | `scripts/acceptance/sa014-empty-repo-version-evidence.sh` + `mvn -pl releasehub-application -Dtest=VersionExtractorTest,CodeRepositoryAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec vitest run src/views/repository/__tests__/RepositoryDetail.spec.ts src/views/repository/__tests__/RepositoryDrawer.spec.ts` | `tasks/records/2026-05-23-sa-014-empty-repo-version-evidence.md` | 真实 GitLab focused 验收 **23 PASS / 0 FAIL**，报告 `.ai/reports/sa014-empty-repo-version/20260523-113039/summary.md`；应用层 22 PASS，页面组件 6 PASS；空仓库不填充假版本、不阻塞仓库列表 |
| SA-002 存量清理人工复核入口 | `mvn -f backend/pom.xml -pl releasehub-application -am -Dtest=DataQualityCleanupReviewAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` + `mvn -f backend/pom.xml -pl releasehub-bootstrap -am -Dtest=DataQualityCleanupApiTest -Dsurefire.failIfNoSpecifiedTests=false test` + `scripts/acceptance/sa002-safe-cleanup.sh --report-dir .ai/reports/sa002-safe-cleanup/manual-review` + 静态扫描 | `tasks/records/2026-05-23-sa-002-cleanup-review-entry.md` | 应用层 **6 PASS / 0 FAIL / 0 SKIP**；API **1 PASS / 0 FAIL / 0 SKIP**；dry-run 报告生成 3 条待复核动作，动作 JSONL 按行合法，`--execute` 继续拒绝；静态扫描报告 `.ai/reports/static-scan/20260523-152446/summary.md` |
| SA-004 GitLab 连接异常诊断展示 | `mvn -q -pl releasehub-infrastructure -am -Dtest=GitLabAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test` + `mvn -q -pl releasehub-bootstrap -am -Dtest=SettingsApiTest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec vitest run src/views/settings/__tests__/Settings.spec.ts` + `pnpm i18n:lint` | `tasks/records/2026-05-23-sa-004-gitlab-connection-diagnostics.md` | token 无效、权限不足、服务不可达后端分类和页面内诊断提示通过；错误文案不携带敏感参数 |
| SA-006 历史不合规分支治理入口 | `mvn -q -pl releasehub-application -am -Dtest=BranchGovernanceAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` + `mvn -q -pl releasehub-bootstrap -am -Dtest=RepositorySyncApiTest -Dsurefire.failIfNoSpecifiedTests=false test` + `mvn -q -pl releasehub-infrastructure -am -Dtest=GitLabGitBranchAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec vitest run src/views/repository/__tests__/RepositoryDrawer.spec.ts src/views/repository/__tests__/RepositoryDetail.spec.ts` | `tasks/records/2026-05-23-sa-006-branch-governance-entry.md` | 仓库维度只读治理清单、API 契约、GitLab 空前缀列全量分支和前端可见边界通过；不执行自动批量修复 |
| SA-013 发布编排 Run 页面内复核 | `mvn -q -pl releasehub-bootstrap -am -Dtest=RunPagedApiTest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec vitest run src/views/release-window/__tests__/OrchestrationPanel.spec.ts` + `pnpm run typecheck` + `pnpm i18n:lint` | `tasks/records/2026-05-23-sa-013-orchestration-run-review.md` | 最近 Run 按 `startedAt DESC` 返回；窗口详情编排面板可见最新 Run 结果、失败上下文、失败步骤和失败原因 |
| SA-003 code 自动生成当前测试证据 | `mvn -pl releasehub-application -am -Dtest=GroupAppServiceValidationTest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec playwright test e2e/tests/slice-1-group-window.spec.ts -g "auto-generated codes"` + `pnpm run test:e2e:slice-1` + `pnpm run typecheck` + `pnpm i18n:lint` + 静态扫描 | `tasks/records/2026-05-23-sa-003-code-auto-generation.md` | 后端 **15 PASS / 0 FAIL / 0 SKIP**；定向外部 Playwright **1 PASS / 0 FAIL / 0 SKIP**；Slice-1 全量 **13 PASS / 0 FAIL / 0 SKIP**；静态扫描报告 `.ai/reports/static-scan/20260523-133505/summary.md` |
| SA-003 分组父级树选择体验 | `pnpm exec vitest run src/components/common/__tests__/GroupTreeSelect.spec.ts src/views/group/__tests__/GroupDialog.spec.ts` + `pnpm run typecheck` + `pnpm i18n:lint` + `pnpm exec playwright test e2e/tests/slice-1-group-window.spec.ts -g "move empty leaf group"` + `pnpm run test:e2e:slice-1` + 静态扫描 | `tasks/records/2026-05-23-sa-003-parent-tree-selector.md` | 前端 Vitest **3 PASS / 0 FAIL / 0 SKIP**；定向外部 Playwright **1 PASS / 0 FAIL / 0 SKIP**；Slice-1 全量 **12 PASS / 0 FAIL / 0 SKIP**；静态扫描报告 `.ai/reports/static-scan/20260523-130022/summary.md` |
| SA-003 受控空叶子分组移动 | `mvn -pl releasehub-application -am -Dtest=GroupAppServiceValidationTest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec vitest run src/utils/__tests__/groupMoveProtection.spec.ts src/views/group/__tests__/GroupDialog.spec.ts` + `pnpm run typecheck` + `pnpm i18n:lint` + 静态扫描 | `tasks/records/2026-05-23-sa-003-governed-empty-leaf-move.md` | 后端 **12 PASS / 0 FAIL / 0 SKIP**；前端 **3 PASS / 0 FAIL / 0 SKIP**；受控移动不破坏叶子资源归属不变量；静态扫描报告 `.ai/reports/static-scan/20260523-012026/summary.md` |
| SA-003 资源创建叶子分组前端断言 | `cd frontend && pnpm run test:e2e:slice-1` | `tasks/records/2026-05-20-sa-003-leaf-group-resource-e2e.md` | **11 PASS / 0 FAIL / 0 SKIP**；仓库、迭代、发布窗口创建入口均稳定断言非叶子分组禁用 |
| SA-010 解除挂载 GitLab 分支归档复核 | `bash scripts/acceptance/run-acceptance.sh` | `tasks/records/2026-05-20-sa-010-detach-gitlab-archive.md` | **142 PASS / 0 FAIL / 0 SKIP**；新增 4.2 证据段验证 attach 后 release 分支存在、detach 后原分支删除且归档分支存在 |
| SA-005 仓库详情组织路径和版本状态 | `mvn -pl releasehub-bootstrap -am -Dtest=RepositorySyncApiTest,RepositoryE2ETest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec vitest run src/views/repository/__tests__/RepositoryDrawer.spec.ts` | `tasks/records/2026-05-21-sa-005-repository-detail-observability.md` | 后端初始版本来源、仓库 E2E 和前端详情展示通过；静态扫描通过 |
| SA-005 Clone URL 纳管保护 | `mvn -pl releasehub-domain,releasehub-application -am -Dtest=CodeRepositoryTest,CodeRepositoryAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` + `mvn -pl releasehub-bootstrap -am -Dtest=RepositoryE2ETest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec vitest run src/utils/__tests__/cloneUrl.spec.ts` | `tasks/records/2026-05-21-sa-005-clone-url-guard.md` | 后端领域/应用 **18 PASS / 0 FAIL / 0 SKIP**，仓库 E2E **11 PASS / 0 FAIL / 0 SKIP**；前端 Clone URL 工具 **2 PASS / 0 FAIL / 0 SKIP**；重复和格式错误在创建/更新前被拒绝 |
| SA-005 版本解析失败修复引导 | `pnpm exec vitest run src/views/repository/__tests__/RepositoryDetail.spec.ts src/views/repository/__tests__/RepositoryDrawer.spec.ts src/utils/__tests__/cloneUrl.spec.ts` + `pnpm run typecheck` + `pnpm i18n:lint` | `tasks/records/2026-05-21-sa-005-version-repair-guidance.md` | 前端 **5 PASS / 0 FAIL / 0 SKIP**；详情页仅在未解析/无版本时展示重新解析入口，成功后刷新版本状态 |
| SA-005 仓库组织筛选 | `mvn -pl releasehub-domain,releasehub-application -am -Dtest=CodeRepositoryTest,CodeRepositoryAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` + `mvn -pl releasehub-bootstrap -am -Dtest=RepositoryE2ETest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec vitest run src/views/repository/__tests__/RepositoryList.spec.ts src/views/repository/__tests__/RepositoryDetail.spec.ts src/views/repository/__tests__/RepositoryDrawer.spec.ts src/utils/__tests__/cloneUrl.spec.ts` + `pnpm run typecheck` + `pnpm i18n:lint` | `tasks/records/2026-05-21-sa-005-repository-group-filter.md` | 应用/领域 **19 PASS / 0 FAIL / 0 SKIP**；仓库 E2E **12 PASS / 0 FAIL / 0 SKIP**；前端 **6 PASS / 0 FAIL / 0 SKIP**；父组织筛选包含子组织仓库 |
| SA-005 删除保护扩展 | `mvn -pl releasehub-application -am -Dtest=CodeRepositoryAppServiceTest,GroupAppServiceValidationTest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec vitest run src/utils/__tests__/deleteProtection.spec.ts src/views/repository/__tests__/RepositoryList.spec.ts src/views/group/__tests__/GroupList.spec.ts` | `tasks/records/2026-05-21-sa-005-delete-protection.md` | 后端 **20 PASS / 0 FAIL / 0 SKIP**；前端 **5 PASS / 0 FAIL / 0 SKIP**；仓库/分组删除保护和前端阻断提示闭环 |
| SA-009 同分组仓库选择与写入保护 | `mvn -pl releasehub-application -am -Dtest=IterationAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec vitest run src/views/iteration/__tests__/AddReposDialog.spec.ts` + `pnpm run typecheck` + `pnpm i18n:lint` | `tasks/records/2026-05-21-sa-009-same-group-repo-selection.md` | 后端 **18 PASS / 0 FAIL / 0 SKIP**；前端 **1 PASS / 0 FAIL / 0 SKIP**；跨分组仓库在写入前被 `ITER_005` 拒绝 |
| SA-009 已挂窗口后迭代仓库集合锁定 | `mvn -pl releasehub-application -am -Dtest=IterationAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` + `mvn -pl releasehub-interfaces -am -DskipTests compile` + `pnpm exec vitest run src/views/iteration/__tests__/AddReposDialog.spec.ts src/views/iteration/__tests__/IterationDetail.spec.ts` | `tasks/records/2026-05-21-sa-009-attached-iteration-scope-lock.md` | 后端 **21 PASS / 0 FAIL / 0 SKIP**；前端 **2 PASS / 0 FAIL / 0 SKIP**；已挂窗口迭代仓库集合和分组不可继续变更 |
| SA-009 移除仓库真实 GitLab 归档 | `bash scripts/acceptance/sa009-remove-repo-gitlab-evidence.sh` + `mvn -q -pl releasehub-application -am -Dtest=IterationAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | `tasks/records/2026-05-23-sa-009-remove-repo-gitlab-archive.md` | 专用真实 GitLab 验收 **22 PASS / 0 FAIL**；应用层回归通过；未挂窗移除归档和已挂窗拒绝移除均可复核 |
| SA-009 大规模迭代仓库可观测性 | `mvn -pl releasehub-application -am -Dtest=IterationAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` + `mvn -pl releasehub-bootstrap -am -Dtest=IterationRepoApiTest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec vitest run src/views/iteration/__tests__/IterationDetail.spec.ts` | `tasks/records/2026-05-23-sa-009-large-iteration-repo-observability.md` | 应用层 **23 PASS / 0 FAIL / 0 SKIP**；接口 **2 PASS / 0 FAIL / 0 SKIP**；前端 **3 PASS / 0 FAIL / 0 SKIP**；大规模迭代仓库分页、摘要和版本/分支记录可追溯 |
| SA-010 同分组挂载范围约束 | `mvn -pl releasehub-application -am -Dtest=AttachAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm run typecheck` + `pnpm i18n:lint` | `tasks/records/2026-05-20-sa-010-group-scope-attach.md` | **7 PASS / 0 FAIL / 0 SKIP**；前端挂载弹窗禁选非同分组迭代，静态扫描通过 |
| SA-010 发布后发布计划变更锁定 | `mvn -pl releasehub-application -am -Dtest=AttachAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec vitest run src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts` | `tasks/records/2026-05-20-sa-010-plan-lock-after-publish.md` | 后端 **9 PASS / 0 FAIL / 0 SKIP**；前端 **4 PASS / 0 FAIL / 0 SKIP**；发布后发布计划不可继续变更 |
| SA-010 解除挂载外部 Playwright 页面复核 | `cd frontend && pnpm run test:e2e:slice-1` | `tasks/records/2026-05-19-sa-010-detach-ui-e2e.md` | **11 PASS / 0 FAIL / 0 SKIP**；新增详情页解除挂载用户旅程 |
| SA-011 Git 访问异常前端旅程 | `pnpm exec playwright test e2e/tests/slice-2-full-flow.spec.ts` | `tasks/records/2026-05-20-sa-011-git-access-evidence.md` | **23 PASS / 0 FAIL / 0 SKIP**；新增 `GIT_PERMISSION_DENIED` / `GIT_UNAVAILABLE` 类型分布、阻断级别、建议处理方式和外部 Git 访问处理入口 |
| SA-012 REPO_AHEAD 接受仓库版本 | `mvn -pl releasehub-application -am -Dtest=IterationAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec vitest run src/views/release-window/__tests__/ConflictPanel.spec.ts src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts` + `pnpm run typecheck` + `pnpm i18n:lint` | `tasks/records/2026-05-21-sa-012-repo-ahead-use-repo.md` | 后端 **22 PASS / 0 FAIL / 0 SKIP**；前端 **7 PASS / 0 FAIL / 0 SKIP**；`REPO_AHEAD` 可走 `USE_REPO` |
| SA-015 Run 详情失败项重试前端闭环 | `pnpm exec vitest run src/views/run/__tests__/RunDetail.spec.ts` + `pnpm run typecheck` + `pnpm i18n:lint` | `tasks/records/2026-05-20-sa-015-run-detail-retry.md` | **2 PASS / 0 FAIL / 0 SKIP**；Run 详情页只提交 `FAILED`/`MERGE_BLOCKED` RunItem key 并跳转到 retry 新 Run |
| SA-014 批量版本更新前端入口 | `pnpm exec vitest run src/views/release-window/__tests__/VersionUpdateDialog.spec.ts` + `pnpm run typecheck` + `pnpm i18n:lint` | `tasks/records/2026-05-20-sa-014-batch-version-update-ui.md` | **2 PASS / 0 FAIL / 0 SKIP**；多仓窗口版本更新弹窗可提交批量版本更新请求 |
| SA-014 Maven 多模块 / Gradle 真实写回 | `mvn -pl releasehub-infrastructure -Dtest=MavenVersionUpdaterTest,GradleVersionUpdaterTest test` + `bash scripts/acceptance/run-acceptance.sh` | `tasks/records/2026-05-20-sa-014-multi-gradle-real-writeback.md` | 更新器单测 **12 PASS / 0 FAIL / 0 SKIP**；真实 GitLab 证据复核 **154 PASS / 0 FAIL / 0 SKIP** |
| SA-014 批量版本更新多仓部分失败 | `bash -n scripts/acceptance/run-acceptance.sh` + `git diff --check` + `bash scripts/acceptance/run-acceptance.sh` | `tasks/records/2026-05-20-sa-014-batch-partial-failure.md` | 真实 GitLab 证据复核 **159 PASS / 0 FAIL / 0 SKIP**；成功项写回、失败项原因和 RunItem 结果均可追溯 |
| SA-014 版本更新失败重试 | `mvn -pl releasehub-application -am -Dtest=RunAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` + `mvn -pl releasehub-infrastructure -am -DskipTests compile` + `mvn -pl releasehub-bootstrap -am -Dtest=WindowRunApiTest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec vitest run src/views/run/__tests__/RunDetail.spec.ts` | `tasks/records/2026-05-21-sa-014-version-update-retry.md` | 后端 **10 PASS / 0 FAIL / 0 SKIP**；MockMvc/JPA **1 PASS / 0 FAIL / 0 SKIP**；前端 **3 PASS / 0 FAIL / 0 SKIP**；retry 只重放 `VERSION_UPDATE_FAILED` 并保留来源追溯 |
| SA-015 前端场景复跑与证据边界更新 | 后端 provider 专项测试 + 前端组件/类型/i18n + Slice-2 Playwright + 静态扫描 | `tasks/records/2026-05-23-sa-015-frontend-scenario-rerun.md` | 后端 **12 PASS / 0 FAIL / 0 SKIP**；组件 **17 PASS / 0 FAIL**；Slice-2 **23 PASS / 0 FAIL**；静态扫描 `.ai/reports/static-scan/20260523-212419/summary.md`；Mock provider 后续仅限隔离测试，不作为产品 UI 持久化数据 |
| SA-011 版本领先风险前端观察 | `pnpm exec playwright test e2e/tests/slice-2-full-flow.spec.ts` | `docs/reports/scenario-acceptance-matrix.md` | **22 PASS / 0 FAIL / 0 SKIP**；新增 `REPO_AHEAD` / `SYSTEM_AHEAD` 详情观察和同步请求语义断言 |
| SA-016 发布窗口报告导出 | `mvn -pl releasehub-bootstrap -am -Dtest=WindowRunApiTest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec vitest run src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts` + `pnpm run typecheck` + `pnpm i18n:lint` | `tasks/records/2026-05-17-sa-016-release-report-export.md` | 后端 JSON/CSV 报告导出和前端详情页 CSV 导出入口通过 |
| SA-016 发布报告制品包归档 | `mvn -pl releasehub-bootstrap -am -Dtest=WindowRunApiTest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec vitest run src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts` + `pnpm run typecheck` + `pnpm i18n:lint` + 静态扫描 | `tasks/records/2026-05-23-sa-016-report-artifact-package.md` | 后端 **1 PASS / 0 FAIL / 0 SKIP**；前端 **8 PASS / 0 FAIL / 0 SKIP**；ZIP 包内 manifest、JSON、CSV、Markdown 可解包复核；静态扫描报告 `.ai/reports/static-scan/20260523-134733/summary.md` |
| SA-016 CI pipeline 触发状态（2026-05-21 历史） | `mvn -pl releasehub-application -am -Dtest=RunAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | `tasks/records/2026-05-21-sa-016-ci-trigger-status.md` | 后端 **12 PASS / 0 FAIL / 0 SKIP**；当时的 `CI_TRIGGERED` 保留 pipeline id，`CI_NOT_CONFIGURED` 不伪装成 `SUCCESS` |
| SA-016 关闭后 GitLab 收尾证据 | `mvn -pl releasehub-application -am -Dtest=RunAppServiceTest,IterationAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` + `bash -n scripts/acceptance/run-acceptance.sh` + `bash scripts/acceptance/run-acceptance.sh` + 静态扫描 | `tasks/records/2026-05-23-sa-016-close-gitlab-evidence.md` | 后端 **37 PASS / 0 FAIL / 0 SKIP**；真实 GitLab 验收 **169 PASS / 0 FAIL / 0 SKIP**；关闭后真实 GitLab merge/tag/archive 复核通过，SA-011 Git 访问异常探针不再因 setup 外部 I/O 异常 500；静态扫描报告 `.ai/reports/static-scan/20260523-150144/summary.md` |
| SA-016 种子分支清理执行保护 | `bash -n scripts/e2e/reset-gitlab-seed-branches.sh` + `scripts/e2e/reset-gitlab-seed-branches.sh --report-dir .ai/reports/gitlab-seed-branch-reset/sa016-dry-run-before` + `scripts/e2e/reset-gitlab-seed-branches.sh --execute --report-dir .ai/reports/gitlab-seed-branch-reset/sa016-execute` + `scripts/e2e/reset-gitlab-seed-branches.sh --execute --report-dir .ai/reports/gitlab-seed-branch-reset/sa016-execute-repeat` + 静态扫描 | `tasks/records/2026-05-23-sa-016-seed-branch-cleanup-evidence.md` | dry-run 候选 **799**；execute 删除 **799** 且 `POST_REMOVED=799`；repeat execute 删除 **0**；7 个 seed branch 全程保留，JSONL 按行合法；静态扫描报告 `.ai/reports/static-scan/20260523-154248/summary.md` |
| URL 双重 encode 修复连带 release 分支创建 | 同上场景 4 | 同上 | 1/3 → **3/3** |
| Listener 异常隔离 | 同上后端日志 | 同上 | UnexpectedRollback 出现次数 2 → **0** |
| 前端 Playwright E2E 基线刷新 | `cd frontend && pnpm run test:e2e` | 本会话 2026-05-15 | **29 PASS / 0 FAIL / 0 SKIP**；登录、Slice-1、Slice-2 可跑通，历史显式 skip 已清零 |
| 本地统一环境入口 | `scripts/dev/start-local-env.sh hold/status/stop` | 本会话 2026-05-15 | 后端、前端和前端 `/api` 代理均可用；停止后 8080/5173 无残留监听 |
| Maven 插件版本显式化 | `mvn -pl releasehub-bootstrap -DskipTests validate` | 本会话 2026-05-13 | surefire/failsafe version missing 警告消失，targeted surefire 测试通过 |
| 单测基线 | `mvn test` | 本会话 2026-05-11 | 161 用例全过（含 GitLabGitBranchAdapterTest ENC 同步纠正） |
| 前端 Vitest / typecheck / i18n lint | `pnpm run test -- src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts src/views/release-window/__tests__/VersionUpdateDialog.spec.ts src/views/release-window/__tests__/OrchestrationPanel.spec.ts src/api/modules/__tests__/releaseWindow.spec.ts` / `pnpm run typecheck` / `pnpm i18n:lint` | 2026-05-15 | 25 Vitest 通过；vue-tsc 通过；`CalendarView.vue`、`RepositoryEdit.vue` 既有硬编码中文已清理，i18n lint 通过 |

---

## 4. 进行中事项

| 事项 | 当前状态 | 下一步 | 验收标准 |
|---|---|---|---|
| S3-C2-B5 原 MERGE 未发送意图的安全续发 | B4 已完成本地与固定 SHA 5/5；两路独立契约及消费者核对已收敛，B5 尚未实现 | 先写原请求、发送/结果权、观察版本与跨 JVM 中断的独立 RED | 仅完整未发送 INTENT 可换 Task 当前权；原 operation/attempt/reservation 保留，旧 worker 不能发送或写 UNKNOWN，一次 PUT 的结果可原子完成 |
| 前端用户旅程自动化验证 | 2026-05-24 的 E2E 49/0/0 为历史；C2-A 组件 214 PASS 为沿用基线，B1 无 UI/API 变更；后端 API/GitLab fixture 均不等于页面旅程 | 按 S5 受控范围执行真实 GitLab/UI 全旅程；C2-A Playwright 仅迁移消费者和发现 23 项 | 页面创建/变更业务数据，API/数据库/GitLab 只作后置复核；stub 不替代真实写入证据 |

---

## 5. 已废弃事项

| 事项 | 废弃原因 | 决策证据 | 是否有残留 |
|---|---|---|---|
| `GitLabBranchPort` 旧端口 + Real/MockGitLabBranchAdapter + 测试 | 多 Provider 迁移完成后无人调用 | commit `0e2efb3`（2026-05-11） | 已清理 |
| Puppeteer E2E（历史 62 个） | 迁移到 Playwright（合并精简为 3 spec / 24 case） | PROJECT_PROGRESS.md 2026-05-09 注 | 已清理 |
| 「MVP」措辞 | 已脱离 MVP | commit `9e63a64` | 个别历史文档可能仍存在，非阻塞 |

> 已废弃事项**不得**重新作为新增实现建议，除非用户明确发起新需求。

---

## 6. 当前 Top Priority

> 当前执行任务只从 `docs/execution-roadmap.md` 的唯一 HEAD 选择；台账与场景矩阵记录事实、证据和缺口，不建立并行队首。

| 优先级 | 事项 | 原因 | 验收标准 |
|---|---|---|---|
| HEAD | SA-013 S3-C2-B5 原 MERGE 未发送意图的安全续发 | B4 固定 be1ccbe Full-Link 5/5 已完成；未发送 MERGE 的完整请求与当前权必须独立验证 | 原请求核等、Task 换权、原操作复用、SEND sender 证明、旧观察失效、真实竞争和进程恢复；尚未实现 |
| 后续 | SA-013 其他 INTENT、SEND/UNKNOWN 核对与恢复 | 不在 B5 的原 MERGE 未发送正例内；PUT 前读取的 ALREADY_MERGED/BLOCKED 不伪造本操作结果，现阶段保留 INTENT/guard | 租约过期不授权重发；分别验收未发送意图收口、精确证据、迟到响应与旧 worker，不任意 FOUND 释放 guard |
| 后续 | SA-002 BranchCreationMode 执行计划评审门禁 | 原队列保留，最小 dry-run 已完成 | 评审是否进入执行计划切片；未批准前不实现执行 API、数据库写入、迁移表或 GitLab 操作 |

---

## 7. 关键证据索引

| 证据 | 路径 | 说明 |
|---|---|---|
| 当前研发交付 | `tasks/records/2026-11-21-sa-013-orchestration-consistency.md` | C1 `e5094ba` / Full-Link `44899945002` 为历史 5/5；第 13 节记录 C2-A 本地 RED/GREEN、214 项前端、消费者补验 16/16、真实进程继续和固定 `1761572` / Full-Link `44905843754` 5/5；第 14 节记录 C2-B1 限定接管、各轮本地门禁、`3f810c8`/`5441df6` 交付与固定 SHA Full-Link `44911634004` 5/5；第 15 节记录 C2-B2 的真实 RED、同一源码树定向 189/API 48/普通 700、APPLIED_BRANCH 硬杀、静态及三组提交；固定 cc800362 的 Full-Link 44917254215 为 5/5，已交付；第 16 节记录 B3 独立正反例、403/48/738 最终门禁、真实 MR 进程恢复、d87dfa8/544983b 推送及固定 SHA Full-Link44924378741的5/5；第17节记录B4独立RED、首轮533/2FAIL、最终533/API48/普通789/静态、真实末尾MERGE恢复、23950f9/be1ccbe推送与固定SHA Full-Link44929301318的5/5；第18节记录B5独立契约与反例，尚未实现 |
| C2-B4 最终本地静态扫描 | `.ai/reports/static-scan/20261122-010845/summary.md` | 六份新鲜SpotBugs XML均0、typecheck PASS、lint 0 errors/8既有fixture warnings逐项处置；七份495文件backend快照一致 |
| C2-B3 已交付静态扫描 | `.ai/reports/static-scan/20261122-001110/summary.md` | 六份新鲜 SpotBugs XML 均0、typecheck PASS、lint 0 errors/8既有fixture warnings；逐项保留理由和覆盖边界，同一495文件backend树 |
| C2-B2 已交付静态扫描 | `.ai/reports/static-scan/20261121-224714/summary.md` | 六份新鲜SpotBugs XML均0、typecheck PASS、lint 0 errors/8既有fixture warnings逐项保留；与定向/API/普通回归同一backend树，无前端变更或规则关闭 |
| C2-B1 已交付静态基线 | `.ai/reports/static-scan/20261121-214959/summary.md` | 6 份新鲜 SpotBugs XML 均 0、typecheck PASS、lint 0 errors/8 既有 fixture warnings 逐项保留；首扫 `20261121-213959` raw 实际 4 项，遗漏 CT 与环境误分类已追加纠正，SQL 已修、构造器 EI/CT 精确说明；仅保留B1历史，C2-B2另由本片224714独立验证 |
| C2-A 历史静态扫描 | `.ai/reports/static-scan/20261121-203554/summary.md` | RunTaskWorker 构造器级精确说明；SpotBugs 0、typecheck PASS、lint 0 errors/8 既有 warnings，EOF 与 TopN 已收口 |
| 场景矩阵 | `docs/reports/scenario-acceptance-matrix.md` | C1/C2-A/C2-B1/C2-B2 各自本地及固定 SHA 交付证据；C2-B3 最终本地及固定544983b的Full-Link44924378741为5/5，已交付；B4最终本地533/API48/普通789与静态通过，23950f9/be1ccbe已推送，固定be1ccbe的Full-Link44929301318为5/5；B5独立契约已定义但未实现，其他INTENT/SEND/UNKNOWN与完整UI仍未交付 |
| 发布候选收口报告 | `docs/reports/release-candidate-2026-05-23.md` | 2026-05-24 当时分支已通过受控候选验证，交付证据包已汇总全量验收、前端 E2E、关键组件、SA-002 处置 case 页面验收和静态扫描；仍为 dogfood/staging 候选 |
| 发布候选评审页 | `frontend/src/views/release-governance/ReleaseCandidateReview.vue` + `GET /api/v1/release-governance/candidate-review` + `POST /api/v1/release-governance/candidate-review/signoffs` | 聚合候选结论、验收证据、风险边界、检查清单和人工签核；签核不触发发布、清理、GitLab 或数据状态变更 |
| 数据质量复核队列 | `frontend/src/views/data-quality/DataQualityReviewQueue.vue` + `POST /api/v1/data-quality/cleanup-review` | 导入 dry-run JSONL 后可按资源、风险、状态和资产范围筛选；页面展示数据源口径、命名空间、复核批次、资产范围、保留策略、处置等级、允许动作、回滚边界和审计记录；所有结果仍不允许直接执行 |
| 数据质量处置执行审计设计 | `docs/openspec/changes/update-data-quality-disposition-audit/` + `docs/requirements/in-progress/SA-002-数据质量受控处置执行审计.md` | 定义处置 case、状态机、幂等键、脱敏快照、失败恢复、重复提交处理和处置等级边界；下一步实现最小 case 模型 |
| 数据质量处置 case | `POST /api/v1/data-quality/disposition-cases` + `GET /api/v1/data-quality/disposition-cases` + `frontend/src/views/data-quality/DataQualityReviewQueue.vue` + `frontend/e2e/tests/data-quality-disposition-case.spec.ts` | 从已接受复核动作创建审计 case，支持列表、详情、start/verify/fail/cancel 状态记录；外部页面验收已覆盖导入 dry-run、提交复核、创建 case、查看详情和记录复核通过；不直接修改业务资源 |
| BranchCreationMode 独立迁移服务设计 | `docs/openspec/changes/add-branch-creation-mode-migration-service/` + `docs/requirements/in-progress/SA-002-BranchCreationMode独立迁移服务设计.md` | 定义 dry-run、候选分类、人工映射、受控执行、复核和回滚；当前只完成设计门禁，不实现执行器、不写数据库、不触碰 GitLab |
| BranchCreationMode proposal 评审记录 | `docs/openspec/changes/add-branch-creation-mode-migration-service/review.md` | 结论 `APPROVE_DRY_RUN_ONLY`；下一步只实现只读 dry-run，执行器和数据库写入继续暂缓 |
| BranchCreationMode dry-run API | `POST /api/v1/data-quality/branch-creation-mode-migrations/dry-run` | 返回四类候选、统计、JSON 结构和 Markdown 报告；`executionPermitted=false`，不执行迁移 |
| 前端 E2E 基线 | `frontend/e2e/tests` | 2026-05-24 完整 E2E 回归：49 PASS / 0 FAIL；覆盖登录、版本策略继承、Slice-1 分组/窗口和 Slice-2 发布链路。入口 `cd frontend && pnpm run test:e2e` |
| v0.1.11 真实 GitLab 报告 | `docs/reports/acceptance-v0.1.11-real-gitlab.md` | 25 PASS / 0 FAIL / 1 SKIP |
| 上轮验收报告 | `docs/reports/archive/acceptance-v0.1.10-real-gitlab.md` | 20/20 PASS，含 2 处已知限制 |
| 验收脚本 | `scripts/acceptance/run-acceptance.sh` + `scripts/acceptance/sa009-remove-repo-gitlab-evidence.sh` | B2 已迁移 close 的 Run 回执、显式 CI、精确 Tag 和 RETAIN 断言，仅语法验证；历史真实 GitLab 结果按日期读取，未重跑本轮完整脚本 |
| 存量数据安全清理 | `scripts/acceptance/sa002-safe-cleanup.sh` + `POST /api/v1/data-quality/cleanup-review` | SA-002 dry-run 清理报告、人工复核入口和处置策略；输出 `summary.md`、`actions.md`、`actions.jsonl`，`summary.md` 使用 `API_VISIBLE_ASSETS`、`DB_AUDIT_ASSETS`、`REVIEW_QUEUE_ACTIONS` 区分应用可见资产、数据库审计资产和复核队列动作；接口拒绝越权执行，响应保留处置等级、允许动作、回滚边界和审计记录 |
| 本地开发库清理 | `scripts/dev/cleanup-dev-database.sh` | 本地开发阶段可重复执行的业务数据清理入口；默认 dry-run，`--execute` 才清理候选业务表；仅允许 `releasehub-postgres/release_hub`，不删除 schema、Flyway 元数据、用户和系统设置，不触碰 GitLab 远端 |
| 版本解析异常诊断 | `GET /api/v1/repositories/{id}/initial-version` | 2026-05-23 SA-014 异常样本治理；返回 `VERSION_FILE_MISSING` / `VERSION_DECL_MISSING` / `VERSION_INVALID` / `VERSION_READ_ERROR`、默认分支、检查路径和说明文案 |
| 本地统一启停脚本 | `scripts/dev/start-local-env.sh` | `start|hold|stop|restart|status`；推荐用 `hold` 托管前后端联调环境 |
| 种子初始化 | `scripts/e2e/init-gitlab.sh` | 幂等，3 个种子仓库 |
| 种子分支清理 | `scripts/e2e/reset-gitlab-seed-branches.sh` | 默认 dry-run 并输出 `summary.md`、`branches.md`、`branches.jsonl`；`--execute` 清理非种子分支，保留 main 与 seed feature 分支，并记录执行后 `POST_KEEP/POST_REMOVED` |
| 启动脚本 | `backend/scripts/run.sh` | `mvn spring-boot:run -pl releasehub-bootstrap` |
| 本地容器 | `releasehub-postgres`(5433) + `releasehub-gitlab`(9080) | 模式 A 常驻；端口策略见 memory `feedback_mode_a_b_port_isolation.md` |
| Flyway 最新迁移（已随 C2-B1 交付） | `V40__add_task_claim_expiry.sql` | 可空 claim_expires_at 与现有 JPA 映射已本地验证；NULL 不从 started_at 回填，local 仅验证新列。V39 任务/Step/operation 事实与 V38/V37 历史证据保留 |
| C2-B3 已交付测试基线 | `.ai/reports/validation/c2-b3/final-green-v2-jdk21/`、`final-gates-v2/` | 定向403/403、API48/48、普通738（733PASS/5既有SKIP）、静态001110，同一backend树；APPLIED_MR纯DB补检查点HTTP0，全场景MR POST1/mergePUT1；d87dfa8/544983b已推送，固定544983b的Full-Link44924378741为5/5 |
| C2-B2 已交付测试基线 | `.ai/reports/validation/c2-b2/initial-green-jdk21/`、`final-gates/` | 定向189/189、API48/48、普通700（695PASS/5既有SKIP），静态224714通过，所有阶段同一稳定backend树；APPLIED_BRANCH 纯 DB 投影 HTTP 0，固定 cc800362 的 Full-Link 44917254215 为 5/5；UI 214 仅旧基线 |
| C2-B1 已交付测试基线 | 本轮研发记录第 14 节 | 静态整理前 API 48/48、普通后端 675 PASS/5 既有 SKIP；最终 SQL 后组合再次 76/76（PG31/HTTP33/process2/worker7/scope3），各轮 hash 一致。最终静态通过；无 UI/API 改动，214 仅沿用，固定 `5441df6` Full-Link 44911634004 为5/5；不代替C2-B2独立本地门禁或远端交付证据 |
| C2-A 历史测试基线 | 本轮研发记录第 13 节、`.ai/reports/validation/c2a-final-*.log`、`.ai/reports/validation/c2-a-full-link/` | C2-A 普通后端 672 PASS/5 既有 SKIP、API/PG 67/67、HTTP 29/29、独立进程 1/1、后补 worker 7/7、前端 214/214、消费者补验 16/16；固定 SHA Full-Link 5/5，各层及重叠 suite 不累加 |
| B2 历史测试基线 | `.ai/reports/validation/b2-backend-final.log`、`b2-api-final.log`、`b2-frontend-final.log` | 普通后端 608 PASS/5 既有 SKIP；六类数据库/API/HTTP fixture 70 PASS；前端 149 PASS，仅代表 B2 当时范围 |
| 项目变更日志 | `docs/PROJECT_PROGRESS.md` | 246 行，承担「变更日志」职责，事实基线请看本文件 |
| 需求索引 | `docs/requirements/INDEX.md` | 进行中：SA-002 BranchCreationMode 独立迁移服务设计、SA-002 数据质量受控处置执行审计 |

---

## 8. 与其它文档的关系

- **本文件 vs `PROJECT_PROGRESS.md`**：本文件是「事实台账」（当前是什么），PROGRESS 是「变更日志」（变了什么）
- **本文件 vs `requirements/INDEX.md`**：INDEX 关注需求生命周期；本文件关注**实现 vs 验证**的差额
- **本文件 vs `openspec/changes/*/tasks.md`**：tasks.md 关注单个 change 的细粒度复选框；本文件提供项目级视图
- **维护规则**：每次发版（v0.1.x）的 commit 必须同时改本文件第 2/3/6/7 节，否则会重新漂移
