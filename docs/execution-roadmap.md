# Execution Roadmap / 执行路线图

> 本文件只回答下一步做什么；正式生命周期与责任读取 `docs/context/PROJECT_PROFILE.yaml`，实现和验收事实以 `docs/project-ledger.md`、`docs/reports/scenario-acceptance-matrix.md` 和任务记录为准。

## 1. 当前唯一主线

2026-11-21 用户授权持续研发、必要重构、分组提交与分阶段推送。先统一可核实的编排语义，再接入可恢复执行。已完成分支收敛：main/develop 同指 `d4534cc`，研发从 `feature/orchestration-consistency` 推进。

- 任务只从下表唯一 HEAD 选择；HEAD 是当前实施队首，不是生命周期或已完成标记。按确认目标与完整蓝图推进，不从阶段标签自动裁剪范围。
- 每个切片独立定义正反例，核实已有职责与实际消费者，验收后继续下一切片。
- 历史验收不能替代新行为验证；测试替身不等于真实 GitLab 或完整页面旅程。

## 2. 当前执行队列

| 顺序 | 标记 | SA | 任务 | 来源 | 选择理由 |
|---|---|---|---|---|---|
| 1 | DONE | SA-013 | S3-A/B1 唯一编排语义与阶段交付 | 提交 53eed67、a4007f2、71a1487 已分阶段推送 | 纯配置、不可变计划、MR-first、精确重试、统一状态与写入证据完成目标验收 |
| 2 | DONE | SA-013 | S3-B2 仓库级关闭受理、收尾失败传播与共享引用保护 | e127023、5ad0666 已推送 | 重复 close 返回原 Run；精确 Tag/显式 CI；共享引用保留；生命周期并发屏障通过验证 |
| 3 | DONE | SA-013 | S3-C1 持久四动作与安全中断 | `44e742c`、`3766686`、`4c6b3e9`、`e5094ba` 已推送；Full-Link 44899945002 为 5/5 | 唯一持久四动作、请求幂等、MR 检查点、原子结果和安全中断已完成本阶段验收 |
| 4 | DONE | SA-013 | S3-C2-A 持久扫描、异步受理与进度观察 | 四组提交已推送；固定 `1761572` 的 Full-Link 44905843754 为 5/5 | 202、持久扫描、PENDING 重启继续与原回执观察完成本阶段验收；不冒称 RUNNING/UNKNOWN 接管 |
| 5 | DONE | SA-013 | S3-C2-B1 无写意图的过期 RUNNING 接管 | 3f810c8、5441df6 已推送；固定 5441df6 的 Full-Link 44911634004 为 5/5 | 无 operation/Step 的完整过期领取按原 token 接管，原任务/首次时间/实际顺序与 MR 检查点保持 |
| 6 | DONE | SA-013 | S3-C2-B2 建分支成功证明与 APPLIED 结果投影恢复 | fe4c006、1386ccc、cc80036 已推送；固定 cc80036 的 Full-Link 44917254215 为 5/5 | 规范成功证明与纯 DB 原分支结果投影完成；同一 backend 树定向 189/API 48/普通 700 及静态门禁通过 |
| 7 | DONE | SA-013 | S3-C2-B3 创建 MR 完整证明与原检查点恢复 | d87dfa8、544983b 已推送；固定完整 SHA `544983beff74604852c050209e4c8caa93678ef2` 的 Full-Link 44924378741 为 5/5；本地 403/48/738 与静态门禁同树 | 完整创建证明、原 ENSURE_MR 检查点投影与后继原 IID/SHA 消费完成本片验收，证据见记录 16.6 |
| 8 | DONE | SA-013 | S3-C2-B4 合并 MR 完整证明与末尾原任务结果恢复 | 23950f9、be1ccbe 已分阶段推送；固定完整 SHA `be1ccbe61a5155ba5ce8582f884d41dbb385a932` 的 Full-Link 44929301318 为 5/5；同一 backend 树 533/48/789 与静态门禁通过 | 完整 MERGE_MR 证明、无 PENDING 的末尾发现与纯 DB 原 TRY 结果已验收，证据见记录 17.5 |
| 9 | DONE | SA-013 | S3-C2-B5 原 MERGE 未发送意图的安全续发 | 后端 0642828、文档 62d075c 已分阶段推送；固定后端完整 SHA 的 Full-Link 44935216788 为 5/5；本地 692 分批汇总/48/816 及静态通过 | 当前发送权与历史事实分责、未发送意图安全续发完成本片验收，详细证据见记录 18.9 |
| 10 | DONE | SA-013 | S3-C2-B6 原 MERGE 未发送意图的本地结果收口 | 实现 4e992e9、文档 29ec005 已推送；固定实现完整 SHA 的 Full-Link 44940092094 为 5/5；单次相关 749/API 48/普通 816、前端 217 与静态通过 | 原 NOT_SENT/结果/guard 原子收口、SEND 无响应保护及 UNKNOWN 限制已验收，最终证据见记录 19.9 |
| 11 | DONE | SA-013 | S3-C2-B7 原 CREATE_BRANCH 未发送意图的安全接续与本地收口 | 00def779 已推送；独立 RED→定向 91/91、单次相关 840/840、API 48、普通 831、静态及源码绑定通过；固定实现完整 SHA 的 Full-Link 44945748427 attempt 2 为 5/5，记录 20.9 | 原 SHA/项目身份、真实 NOT_SENT、SEND 边界和跨 JVM APPLIED 投影已验收；首轮 Central 429 保留，验收文档单独收口 |
| 12 | DONE | SA-013 | S3-C2-B8 正常 CREATE_MR 完整请求的发送证明闭环 | 独立 RED→125/125、相关 965/965 与本地门禁通过；实现 537588e 已推送，固定 SHA CI 44972473174 为 5/5 | 正常 Task 与现 cleanup 的完整命令闭环已验；证据见记录21.6–21.11，未开放 CREATE_MR 恢复 |
| 13 | DONE | SA-013 | S3-C2-B9 原 CREATE_MR INTENT 的安全接续与本地收口 | 666acdc 已推送；单次 1078/API 48/普通 918、静态及源码绑定通过；固定 SHA Full-Link 44978661003 attempt 1 为 5/5 | 原 I/A 接续、真实 NOT_SENT、当前 sender 与持久 Step 顺序已验收；记录第22节保留首次失败及完整复验 |
| 14 | DONE | SA-013 | S3-C2-B10 MERGE_MR 精确只读核对证据 | 8dcca5b已推；相关1123/API48/普通942、前端218及静态同树通过；固定SHA CI44982102777首轮5/5 | 原对象完整证明、typed观察和真实finish双向竞争已验；记录第23节保留证据边界 |
| 15 | DONE | SA-013 | S3-C2-B11 GitLab MR 读取响应状态证明 | 35ca213已推；相关1151/API48/普通967及静态同树通过；固定SHA CI44984865795首轮5/5 | 两个既有读取出口共两行生产修复；33例RED/GREEN及分页中断、零写保护已验，记录第24节 |
| 16 | DONE | SA-013 | S3-C2-B12 无自身写意图任务的失败与重试闭环 | 7042f19已推；相关1174/API48/普通967及静态与源码绑定通过；固定SHA CI44989857158首轮5/5 | 当前Task无operation及Step的首次UNKNOWN归FAILED；真实锁竞争/回滚、新Run重试及既有未知保护已验，记录第25节 |
| 17 | HEAD | SA-013 | S3-C2-B13 项目身份响应证明统一 | 三个namespace入口与实际消费者已核；独立测试尚未落盘，尚未运行 | actual200/object/正整数JSON id共用现纯证明，保留各入口政策与long消费范围，不扩大恢复权 |
| 18 | QUEUED | SA-013 | S3-C2-B 后续：SEND/UNKNOWN 受证据约束的恢复 | 身份读取证明补齐后按动作与原响应事实独立验收 | 区分完整同步响应、I/O未确认及已落UNKNOWN Task，不由一次FOUND授予重发或释放保护 |
| 19 | QUEUED | SA-002 | BranchCreationMode 执行计划评审门禁 | 原队列保留 | 历史数据处置仍先核实计划，不自动迁移或清库 |

## 3. 当前队首验收出口

S3-A/B1 已完成单元、H2/PostgreSQL/API、前端门禁、静态扫描与交叉审查，并推送到 feature。A/B1 提交前 Git 基础独立树 194 项测试全过、六模块 SpotBugs 0；两个提交前组合缺陷有真实 RED→GREEN 证据。main/develop 保持共同基线。

B2 已完成：普通后端 613 项（608 PASS、5 既有 SKIP）、六类数据库/API/HTTP fixture 70/70、前端 38 文件/149 项及全部门禁通过。最终静态扫描 `.ai/reports/static-scan/20261121-153304/summary.md`：SpotBugs 0、lint 0 errors/8 既有 fixture warnings，逐项处置已记录。关闭受理与 CLOSED 同事务；共享仓库只执行一次；Tag/CI 失败零后继写；release 缺失零发布副作用；共享 Iteration 和引用保留；重复/并发 close 返回原 Run；在途编排与关闭通过同一窗口锁互斥。

C1 的本地最后候选验证已通过：普通后端 57 类 657 项（652 PASS/5 SKIP），最新契约 9 类 148/148，前端 170/170，以及 MR 检查点提交后独立 JVM 强杀、重启同 key 零新增 HTTP。受理回执已收窄为 runId；迭代配置建分支/归档旧入口已删除，repoConfigs 范围已校验；E2E fixture 使用隔离项目与已验证 Settings。最终扫描 `20261121-175548` 为 SpotBugs 0、typecheck PASS、lint 0 errors/8 既有 warnings，TopN 与 EOF 修正复验已留痕。四组提交已推送，固定 `e5094ba` 的 Full-Link 44899945002 为 5/5，C1 标记 DONE；其硬杀验收边界仍是可查询且安全停住。

C2-A 本地验收已完成：202 与 Git I/O 解耦，默认数据库扫描无需内存唤醒；有界游标、池拒绝/取消、同 Run 双仓真实进展及同仓前序限制均验证。API/PG 67/67、真实 HTTP 29/29、独立 JVM 1/1，后补 worker 7/7；普通后端 677 项（672 PASS/5 SKIP）、前端 39 文件 214/214 和全部门禁通过，重叠测试不累计。MR 检查点后重启只继续原 PENDING TRY_MERGE，MR POST=1、MERGE PUT=1；已领取任务仍安全停住。四组提交 `06aee3b`、`b37397f`、`e0c6356`、`1761572` 已推送；固定最后 SHA 的 Full-Link 44905843754 为 5/5，C2-A 出队。E2E fixture 最后补验 16/16，包含原 RunId 值比较。

C2-A 最终静态报告为 `.ai/reports/static-scan/20261121-203554/summary.md`：SpotBugs 0、typecheck PASS、lint 0 errors/8 既有 warnings，TopN 逐项处理且 EOF 修正复查通过。任意中断点接管与真实 UI 黄金旅程仍未验收。CI profile/Python/fixture 修复链及 C1 5/5 保留为历史；C2-A 已用本次 SHA 独立复验，Slice4/5 的较弱覆盖不扩写为完整清理/真实冲突验收。

当前 C2-B1 已在同一路径实现：API 48/48、普通后端 680 项（675 PASS、5 既有 SKIP）在静态整理前各自源码稳定的树通过；整理仅涉及 RunJpaPersistenceAdapter，最终代码已重跑覆盖它的组合 76/76，源码前后 hash 一致，重叠不累计。末尾仅 RUNNING、双 worker 原子接管、旧 begin/complete 拒绝、全部 operation/当前 task Step 排除、拿锁后 DB 期限、首次时间及已有顺序保留均有真实 RED→GREEN；PENDING 和已领取但无 operation 两个独立 JVM 强杀重启场景均 MR POST=1、MERGE PUT=1。默认期限 60 分钟、无心跳；V40 NULL 不回填为资格。最终静态 `.ai/reports/static-scan/20261121-214959/summary.md` 为六模块 SpotBugs 0、typecheck PASS、lint 0 errors/8 既有 fixture warnings；首扫 4 项已修复或精确说明并复扫。后端 3f810c8、文档 5441df6 已推送；固定完整 5441df6bdc971953dee390c9470d4786fe0eab44 的 [Full-Link 44911634004](https://github.com/tongsh6/release-hub/actions/runs/44911634004) 为 5/5，B1 正式出队，未借用 C2-A CI。

C2-B2 已完成 CREATE_BRANCH 成功证明与原分支 Task 的 APPLIED 投影并分组推送。真实 RED 先复现错误 commit.id 被旁支字段覆盖而错误 CREATED/APPLIED 并清 guard，再统一 201/name/确切 commit.id 校验。复用原事件、operation、Task 与 guard，同一结果事务补记；真实进程 exit=137 后默认 worker 恢复，投影阶段 HTTP=0、分支 POST 总数 1、原 token/三条事件保持，原 Run 随后完成。同一 backend 树组合 189/189、API 48/48、普通 695 PASS/5 既有 SKIP 和静态 20261121-224714 通过，TopN 8 条既有 fixture 告警逐项留痕；详细证据见任务记录 15.2。固定 `cc800362f333418776f4a816e1fa74ee9f08c04f` 的 [Full-Link 44917254215](https://github.com/tongsh6/release-hub/actions/runs/44917254215) 为 5/5，B2 已 DONE。C2-B3 交付证据见记录 16.6，C2-B4 交付证据见记录 17.5。C2-B5 后端 0642828、文档 62d075c 已分阶段推送，固定后端完整 SHA 的 [Full-Link 44935216788](https://github.com/tongsh6/release-hub/actions/runs/44935216788) 为 5/5，B5 已 DONE，证据见记录 18.9。C2-B6 实现 `4e992e9c2406586ec23fa7636292e3d48c163118`、文档 `29ec00586be20730e66cf37da12614e6450d240d` 已分阶段推送，固定实现 SHA 的 [Full-Link 44940092094](https://github.com/tongsh6/release-hub/actions/runs/44940092094) 为 5/5，已 DONE；独立 RED、定向 57、单次相关 749/API 48/普通 816、前端 217、静态与源码绑定事实见记录 19。B7 已完成独立 RED、定向 91/91、单次相关 840/840、API 48/48、普通 831（826 PASS / 5 既有 SKIP）、静态及源码绑定，实现 00def779 已推送；固定实现 SHA 的 Full-Link 44945748427 attempt 2 为 5/5，B7 已 DONE。首轮 Central 429 与最终结果见记录 20.8–20.9，验收文档单独收口。B8 已完成独立 RED、定向 125/125、单次相关 965/965、API 48/48、普通 877（872 PASS / 5 既有 SKIP）与静态验收；实现 `537588e782c2b3bb2d2e82e21804f2d37fb42fc2` 已推送，固定该 SHA 的 [Full-Link 44972473174](https://github.com/tongsh6/release-hub/actions/runs/44972473174) attempt 1 为 5 PASS / 0 FAIL，B8 已 DONE。详见任务记录 21.6–21.11。B9 原 CREATE_MR INTENT 的安全接续与本地收口已验收：复用原项目 I、SHA A、完整请求及 operation/attempt，换权只更新当前 Task 发送资格；真实 noSEND 结果与原 NOT_SENT/Task/Step/metadata/Run/guard 同事务收口。原 SEND/UNKNOWN 继续保留保护，步骤读取复用匹配 Task 的持久顺序。独立正反例先行，最终单次 13 suites 1078/1078（含 113 新例）、API 48/48、普通 918（913 PASS / 5 既有 SKIP）及静态通过；十份 495 文件最终快照相同。实现 `666acdc7a60c0922db2f05713cbfefd23eed8265` 已推送，固定该 SHA 的 [Full-Link 44978661003](https://github.com/tongsh6/release-hub/actions/runs/44978661003) attempt 1 为 5 PASS / 0 FAIL。详见记录第22节。

B10「MERGE_MR 精确只读核对证据」已验收：原 PUT 定位、实际 GET 状态与完整 I/IID/A 证明共同决定观察，十字段保留 JSON 类型和显式 null；原 Task/Step/guard/seq2 保持。冻结 RED 为 23 FAIL/4 正控 PASS，同批 27/27 GREEN；最终单次相关 1123/1123、API 48/48、普通 942（937 PASS/5 既有 SKIP）、前端 218/218及静态通过。实现 `8dcca5bf8265a3a1f36c16a14f0e3b8b0b35652d` 已推送，固定该 SHA 的 [Full-Link 44982102777](https://github.com/tongsh6/release-hub/actions/runs/44982102777) attempt 1 为 5 PASS/0 FAIL。 最终10份backend495、6份frontend222快照分别一致；详见记录第23节。

B11「GitLab MR 读取响应状态证明」已验收：getMap/readPages 仅让实际 HTTP 200 进入原正文与身份校验，正常返回的非 200 为 INCOMPLETE；分页任一页不合格即舍弃已累积候选，既有异常分类保持。冻结独立 RED 为 19 FAIL/14 正控 PASS，同批 33/33 GREEN（28 新例及 5 既有正控）；最终单次相关 1151/1151、API 48/48、普通 967（962 PASS/5 既有 SKIP）及静态通过。实现 `35ca21366ec9124fe9c4d6d2ef162a09c13709fb` 已推送，固定该 SHA 的 [Full-Link 44984865795](https://github.com/tongsh6/release-hub/actions/runs/44984865795) attempt 1 为 5 PASS/0 FAIL。 最终10份backend495、2份frontend222快照分别一致；前端源码不变，B10的218为历史结果，本片未重跑组件测试。详见记录第24节。

B12「无自身写意图任务的失败与重试闭环」已验收：在原Run→Task锁与token核准下，仅将当前Task无任何自身operation且无自身Step的首次UNKNOWN归FAILED，同一结果贯穿原结果事务；诊断、原计划及真实未知写保护保持，现新Run retry继续遵守源Run结束等受理门禁。独立23新例为11 FAIL/12正控PASS→23/23 GREEN；单次相关1174/1174含这23例，API旧fixture补证后48/48、普通967（962 PASS/5旧SKIP）及静态通过。实现 `7042f19278ff8d8bfa102a8f27d0c1f1b949539d` 已推送，固定该SHA的 [Full-Link 44989857158](https://github.com/tongsh6/release-hub/actions/runs/44989857158) attempt 1为5 PASS/0 FAIL。 10份通过阶段backend快照中后6份495文件完全相同，前4份仅未选中的API fixture不同，其余494文件全部相同；2份frontend222快照相同且与B11一致。 详见记录第25节。当前唯一 HEAD 为 B13「项目身份响应证明统一」。三个namespace→numeric project入口将共用现Resolver的actual200/object/正整数JSON id纯证明，各入口定位、token与异常政策保持；GitLabPort的long消费者独立拒绝越界，其余字符串身份不新增long上限。独立正反例仅只读和内存准备，尚未落盘、尚未运行；metadata与其他业务GET证明、SEND/UNKNOWN恢复及完整UI继续独立验收。

C2-B4 已完成独立 RED、最终 11 suites/533 定向、API 48、普通 789（784 PASS/5 既有 SKIP）及静态 20261122-010845；七份 495 文件 backend 快照与提交树一致，TopN 八项既有 warning 已逐项留痕。末尾 MERGE/APPLIED 后无 PENDING 的独立 JVM exit=137，默认 worker 重启投影 HTTP=0、原 merge PUT 总数 1、原 token/六条事件保持；首轮 533/2 FAIL 及仅测试断言修正历史见记录 17.2。后端 `23950f98f2c7000bc054e74e9a67feb5f892e2dc` 与文档 `be1ccbe61a5155ba5ce8582f884d41dbb385a932` 已分阶段推送；固定后者的 [Full-Link 44929301318](https://github.com/tongsh6/release-hub/actions/runs/44929301318) 为 5/5，B4 标记 DONE，覆盖仍限于后端 API 与隔离 GitLab，不扩大完整 UI 或 Slice4/5 边界。

## 4. 暂缓项

| 事项 | 原因 |
|---|---|
| RBAC、通知、CI 深集成 | 与当前编排可靠性目标无关 |
| 批量组织重构或跨 provider 迁移 | 不引入无实际消费者的通用平台 |
| 自动批量删除存量数据 | 仍需明确范围和可核实处置依据 |
| 扩展新的版本更新能力 | 按已确认蓝图修已有文件写入的数据一致性；不因阶段标签扩大或缩小产品范围 |

## 5. 队列约束

- HEAD 必须且只能有一个，SA 编号必须存在于场景矩阵。
- 验收完成后先更新队列再接下一项；未验收能力不得标记完成。
- 修改后运行 `bash scripts/dev/check-roadmap.sh`。
