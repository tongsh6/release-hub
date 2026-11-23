# 实施切片与验收追踪

完整蓝图：[design.md](design.md)。S0、S1、S2 已完成；2026-11-21 用户授权继续研发、必要重构和分阶段推送。S3-A/B1、S3-B2、S3-C1、S3-C2-A、S3-C2-B1 已交付；C1 固定 `e5094ba` 的 Full-Link 44899945002、C2-A 固定 `1761572` 的 Full-Link 44905843754、C2-B1 固定 `5441df6` 的 Full-Link 44911634004 均为 5/5。C2-B1 代码 `3f810c8`、文档 `5441df6` 已分组提交推送。C2-B2 三组 fe4c006、1386ccc、cc80036 已推送，固定 `cc800362f333418776f4a816e1fa74ee9f08c04f` 的 [Full-Link 44917254215](https://github.com/tongsh6/release-hub/actions/runs/44917254215) 为 5/5，建分支成功证明与 APPLIED 投影恢复已交付。C2-B3 的 d87dfa8、544983b 已推送，固定 `544983beff74604852c050209e4c8caa93678ef2` 的 [Full-Link 44924378741](https://github.com/tongsh6/release-hub/actions/runs/44924378741) 为 5/5，B3 已 DONE，完整证据见记录 16.6。C2-B4 的 23950f9、be1ccbe 已分阶段推送，固定完整 `be1ccbe61a5155ba5ce8582f884d41dbb385a932` 的 [Full-Link 44929301318](https://github.com/tongsh6/release-hub/actions/runs/44929301318) 为 5/5，B4 已 DONE（记录 17.5）。C2-B5 后端 0642828、文档 62d075c 已分阶段推送，固定后端完整 SHA 的 Full-Link 44935216788 为 5/5，B5 已 DONE；本地 692 分批汇总/48/816 与各轮历史见记录 18。C2-B6 实现 4e992e9、文档 29ec005 已推送，固定实现完整 SHA 的 Full-Link 44940092094 为 5/5，已 DONE；独立 RED→GREEN、相关单次 749/API 48/普通 816、前端 217、静态及源码绑定见记录 19.9。B7 已完成独立 RED、定向 91/91、单次相关 840/840、API 48/48、普通 831（826 PASS / 5 既有 SKIP）、静态及源码绑定，实现 00def779 已推送；固定实现 SHA 的 Full-Link 44945748427 attempt 2 为 5/5，B7 已 DONE。首轮 Central 429 与最终结果见记录 20.8–20.9，验收文档单独收口。B8 已完成独立 RED、定向 125/125、单次相关 965/965、API 48/48、普通 877（872 PASS / 5 既有 SKIP）与静态验收；实现 `537588e782c2b3bb2d2e82e21804f2d37fb42fc2` 已推送，固定该 SHA 的 [Full-Link 44972473174](https://github.com/tongsh6/release-hub/actions/runs/44972473174) attempt 1 为 5 PASS / 0 FAIL，B8 已 DONE。详见任务记录 21.6–21.11。B9 原 CREATE_MR INTENT 的安全接续与本地收口已 DONE：相关1078/1078、API48/48、普通918（913 PASS / 5既有SKIP）、静态与最终源码审计通过，实现 `666acdc7a60c0922db2f05713cbfefd23eed8265` 已推送，固定该SHA的Full-Link 44978661003 attempt 1为5 PASS / 0 FAIL。证据与边界见记录22.7–22.14，验收文档单独收口。B10「MERGE_MR 精确只读核对证据」已DONE：相关1123/API48/普通942、前端218与静态及源码审计通过，实现 `8dcca5bf8265a3a1f36c16a14f0e3b8b0b35652d` 已推送，固定该SHA的Full-Link44982102777 attempt 1为5 PASS/0 FAIL，证据见记录23.7–23.11。B11「GitLab MR读取响应状态证明」已DONE：实现 `35ca21366ec9124fe9c4d6d2ef162a09c13709fb` 已推送，固定该SHA的Full-Link44984865795 attempt 1为5 PASS/0 FAIL，完整本地与远端证据见记录24.5–24.9。B12「无自身写意图任务的失败与重试闭环」已DONE：实现 `7042f19278ff8d8bfa102a8f27d0c1f1b949539d` 已推送，固定该SHA的Full-Link44989857158 attempt 1为5 PASS/0 FAIL，最终证据见记录25.10。B13「项目身份响应证明统一」已DONE，4dbe2c6/ddcec2e已分组推送，固定ddcec2e的Full-Link45102738088首轮5 PASS/0 FAIL，证据见记录26。当前唯一 HEAD 为 C2-B14「原 MERGE_MR 已持久拒绝响应的结果投影」；事前契约已定义，尚无B14测试或实现，其他SEND/UNKNOWN恢复继续QUEUED，SA-002顺序保持。B14及后续SEND/UNKNOWN恢复仍独立验收，C2整体未完成。

关联：[需求](../../../requirements/in-progress/SA-013-发布编排中断恢复.md)、[提案](proposal.md)、[Run delta](specs/run/spec.md)、[窗口 delta](specs/release-window/spec.md)。依赖顺序为 S0 → S1 → S2 → S3 → S4 → S5。

## S0：设计与现状证据

- 蓝图归属：事实基线、完整目标和风险边界。
- 用户价值：可审阅明确方案，不把现有文档里的 RunTask 当作已实现。
- 涉及层：源码只读、测试、需求/OpenSpec/文档。
- 依赖：无；不改生产逻辑，不执行真实 GitLab 写入。
- [x] 核对 Run / Item 身份、事务、Git 适配器、任务表、API 和 UI 差异。
- [x] 4 项应用级证据：创建分支后丢记录、结果未知被重放、保存失败前已合并、ID 碰撞。
- [x] 2 项 PostgreSQL 证据：跨 Run 执行项归属转移、数据库回滚不撤销模拟外部副作用。
- [x] 完整设计、状态、数据/API/UI、Provider 核对策略与迁移回滚方案。
- [x] 文档结构、引用和静态检查，交付记录收口；见 `tasks/records/2026-10-05-sa-013-run-recovery-design.md`。

## 实施门禁

- [x] 用户已批准提案的 S1 范围（2026-10-05 明确回复“同意”）；不扩展到后续写执行。

## S1：唯一身份与持久计划预览

- 蓝图归属：第 2–5、8 节；先解决历史记录隔离，再建立可查询计划。
- 用户价值：同范围多次运行不再覆盖旧记录，页面可预览持久计划而不写 Git。
- 涉及层：Domain/Application/Infrastructure/API/Frontend/Tests/Migration。
- 依赖：提案和 S1 范围批准；不依赖 S2 的真实写能力。
- 非目标：不启用后台写执行，不轮换凭据、不批量迁移历史记录。
- 回滚：关闭新增预览入口，保留只增结构与 UUID 记录；旧历史查询保持兼容。
- [x] 新 Run/Item 使用独立唯一标识，支持同毫秒并发和重复范围；旧 ID 可读取。
- [x] 将本轮“现状证据”中的 ID/关联断言转为“不碰撞、旧记录不丢项”的回归断言。
- [x] Run 协议版本、幂等键、请求哈希与不可变计划快照持久化。
- [x] 创建/查询计划 API 与页面预览接通，未授权时无写调度入口。
- [x] 相同 key 同请求返回同一结果，异请求拒绝；并发提交约束由数据库兜底。
- [x] 验证计划预览零 Git 写入、历史导出与列表不回归，记录迁移和回滚证据。
- 后续：S2 完成核对与门禁后，S3 才能启用执行。

## S2：操作证据与仓库写入门禁

范围评审及实施批准已完成：[S2 评审方案](s2-review.md)。S2-A → S2-B → S2-C → S2-D，四项均验收后才可将 S2 标记完成。

- [x] 2026-10-08 源码覆盖表、隐藏写路径、独立账本、并发/未知结果验收和回退评审。
- [x] 用户批准 S2 实施及专用本机 GitLab fixture 范围（2026-10-08 明确回复“同意”）。
- [x] S2-A：归档失败零删除、原子 Git 写与精确结果语义。
- [x] S2-B：跨 RunTask 的操作证据、PostgreSQL CAS 和全入口仓库保留。
- [x] S2-C：仓库/Run 证据入口、分页查询、幂等只读核对。
- [x] S2-D：GitLab 17.11.3 隔离能力验证、页面旅程与启用/回退检查。

- 蓝图归属：第 6–7 节。
- 用户价值：用户能看到“已完成、确认未发出、结果未知”的核对结果和原因。
- 涉及层：Git Port/Adapter、Journal、RepositoryGuard、API/页面观察、Tests。
- 依赖：S1；执行器保持关闭，先通过 fixture 和受控验收。
- 回滚：关闭新核对能力，保留已记录证据和仓库阻断，不自动解除未知请求。
- [x] 拆分现有复合 Git 操作，每个写请求有独立 intent 与 attempt。
- [x] 保存 MR IID、提交 SHA 和操作关联标识，适配器区分 UNKNOWN 与确认失败。
- [x] 覆盖 Run、Attach、Iteration、ReleaseBranch、CodeMerge 和文件更新的所有内部写入口；禁止仅“先查询再写”式门禁。
- [x] 冲突检查的临时 MR/PR、独立文件适配器、本地文件写及仓库/连接映射变更纳入保护；reconcile 不调用带副作用的检查。
- [x] 过期 worker CAS、在途请求和仓库保留边界通过并发测试。
- [x] 在 GitLab 17.11.3 fixture 验证实际前置条件与查询能力；不支持能力明确阻断自动恢复。
- [x] 核对接口和页面只读取外部状态，证据不足时无写请求。
- 后续：S3 消费这些能力，不能绕过门禁直接重用旧复合 adapter。

S2 实际行为、验证与限制见 [实施口径](s2-implementation.md)。2026-11-21 持续研发授权已覆盖后续实现，旧审批状态不再阻断研发；不据此开放未验收的真实业务写入。

## S3-A / S3-B：先统一唯一执行语义（2026-11-21 起）

- [x] 核实 HEAD、77 个既有改动及针对性测试基线，独立定义目标与最强反例，记录完整蓝图。
- [x] A1：Attach/Detach 纯配置、批量预校验、重复挂载不重排；删除旧手工 Git 路由。
- [x] A2：GitLabFilePort 读取内容携原 revision、全消费者迁移、A→B→A′ 零覆盖。
- [x] A3：权威范围、同仓串行/FAIL_FAST、完整跳过记录、计划与实际次序；发布不隐式执行。
- [x] A4：统一 Run 聚合状态、前端唯一集成入口、默认策略、权威 scope 和集合计数。
- [x] B1：MR-first 复用、阻塞枚举与证据、UNKNOWN 停止，失败项重试遵循原动作。
- [x] B2：以 `(window, repository)` 原子受理关闭和收尾计划；重复 close 返回原 Run；真实 release/MR 合并 SHA 驱动 Tag、CI 默认关闭且显式选择；失败停止后继；共享分支与 Iteration 保留；生命周期锁及在途屏障。
- [x] A/B1 单测/组件测试 RED→GREEN、真实数据库/API、编译/类型检查、静态扫描 TopN 处理/复扫与代码审查；A/B1 提交前 Git 基础独立树 194 项通过。B2 已单独通过普通后端 613 项（608 PASS、5 既有 SKIP）、六类数据库/API/HTTP fixture 70/70、前端 38 文件/149 项及 typecheck/i18n/lint/build；最终静态扫描 `20261121-153304` 为 SpotBugs 0、lint 0 errors/8 既有 warnings。
- [x] A/B1 按依赖分组提交并分阶段推送：53eed67（Git）、a4007f2（编排）、71a1487（UI）；B2 后端 e127023、前端 5ad0666 已推送并核对远端 hash；证据见本轮任务记录。S3-C 尚未交付。

## S3-C1：持久四动作与安全中断

- 蓝图归属：第 3、5、8 节；先交付业务持久提交点，再扩展调度。
- 用户价值：重复请求得到原执行回执；MR 创建与合并之间有可查询事实；中断或落库失败后不盲目重放。
- 涉及层：既有 run_task、Run/Item/Step、RunPlanPort/RunPort、Git 工作流与 Journal、API/UI 请求 key、迁移及故障验证。
- 依赖：S3-A/B1、B2 已验收。请求线程消费唯一持久任务路径，删除旧 WINDOW_ORCHESTRATION 循环；不保留平行协议。
- 回滚：停止新受理与派发，保留计划、任务、操作证据和仓库保护；旧历史只读，不猜测生成任务。
- [x] 原始 request key 与带动作域的规范化请求指纹；同 actor/key 同请求返回原 Run，异动作或 payload 返回 409；重复 receipt 在动态范围校验前识别。
- [x] Run、Item、不可变计划、四动作任务同事务受理；任何部分失败全部回滚且零 Git 写。
- [x] 短事务领取与持久实际顺序；任务/步骤稳定身份；结果 CAS、Item/Run/绑定投影和 guard 确认同事务，不再整 Run 级联覆盖。
- [x] ENSURE_MR 保存 numeric project/IID/URL/source/target/冻结 SHA；TRY_MERGE 从此证据继续，身份重绑定不得转写新项目。
- [x] Task 所有权约束 journal begin、SEND_STARTED 与结果确认；已 SEND_STARTED 不因超时、代次或一次 404 获得重新发送资格。
- [x] FAIL_FAST 只传播同仓；UNKNOWN 在 CONTINUE 下仍停止同仓；精确 retry 不重放成功动作、不接受未知事实。
- [x] 真实 PostgreSQL/HTTP fixture 验证并发受理、两仓定向提交、HTTP 成功后本地提交失败、旧领取者、真实独立进程硬中断后原 receipt 可查询且零重放。
- [x] 移除迭代 create/update/addRepos 建分支、update/removeRepos 归档的隐式 Git 写；保留命名/版本映射、EXISTING 只读校验、挂窗/同组约束，前端文案与真实 fixture 同步。
- [x] 收口交付：旧 WINDOW 写路径已删除，后端/API/前端/静态扫描/TopN、文档与工作区门禁通过；四组提交已推送，固定 `e5094ba` 的 Full-Link 44899945002 为 5/5。
- 非目标：不提供 worker、自动 resume 或重启后自动继续，不把安全停住描述成恢复完成。

## S3-C2：同一执行器的后台驱动、核对与恢复

- 用户价值：受理后立即返回进度入口，后台持续消费；中断后仅继续有充分证据支持的步骤。
- 依赖：C1 唯一任务协议；版本更新与收尾在 S4 前不宣称同等恢复保障。
- 回滚：停止受理/派发，保留任务和在途保护，核对后再切换；不重启旧 WINDOW 执行路径。

### C2-A：持久扫描、异步受理与进度观察（已验收）

- [x] 两个受理入口返回 202/Run ID，删除请求线程同步驱动；唯一执行器每次领取并完成一个 Task。
- [x] 有界游标扫描当前 WINDOW 协议的 RUNNING/NEEDS_REVIEW 且存在 PENDING 的 Run；资格仍由既有 claimNextTask/eligible 决定，不扫描历史/预览/其他动作。
- [x] 有界 worker 在实际开始后才领取；提交拒绝或排队取消不改数据库，领取后中断不回退 PENDING；不同仓可并行，同仓不越过前序。
- [x] 已保存原请求的回执入口不依赖当前可执行范围；窗口冻结或绑定变化后仍可用原 key/原 payload 查询 receipt，新请求仍严格校验当前范围。
- [x] 原请求回执、202 transport、按 Run/Task 观察以及 E2E/shell 的真实等待消费者同片迁移；轮询不重置选择、不混入另一 Run，不仅以 NEEDS_REVIEW 判断全部停止。
- [x] 实际受理后丢失唤醒、线程池拒绝、双 worker、阻塞前页不饥饿及 MR 检查点后独立进程重启继续通过验证；成功前缀零重放，RUNNING/UNKNOWN 不重领。

本地验收：API/PG 67/67、真实 HTTP 29/29、独立进程 1/1、后补 worker 7/7，普通后端 672 PASS/5 既有 SKIP，前端 214/214；重叠范围不累计。静态 `20261121-203554` 及 TopN 通过。四组提交已推送，固定 `1761572bca6c149aecba78d26a7b25cb98b5251a` 的 Full-Link 44905843754 为 5/5（2026-11-21），未以 C1 5/5 代替；最后 E2E fixture 契约补验 16/16。历史推进中，C2-A、C2-B1 出队后队首曾为 C2-B2；当前唯一队首以执行路线图为准。

### C2-B：已领取任务接管与证据驱动恢复

#### C2-B1：无 operation 的过期 RUNNING 接管（已验收，提交推送完成）

- 蓝图归属：设计 0/3/4/5；复用原任务和单步执行器，先证明无写意图边界。
- 用户价值：领取后、写意图前退出的任务能由默认扫描继续；Run/Item/检查点与成功前缀保持。
- 依赖：C2-A 固定 SHA CI 验收；实现前已完成现有时间、锁序、operation/Step 身份和真实消费者的只读查重。
- [x] 原 Task 新增可空 claim_expires_at，Flyway/JPA 两条实际 schema 路径验证；NULL 不推断恢复资格，local 用例只证明新增列。
- [x] 候选发现与唯一领取一起扩展，Run→Task 锁内按 task_id 排除全部 operation/Step；不新增恢复执行器或旧方法别名。
- [x] Task 首次时间及已有实际顺序保留；领取事实缺失时拒绝接管，不补造顺序。当前 Claim/Step 同 worker 时钟，期限使用拿锁后 DB 时间；默认保守 60 分钟且零/负值拒绝。
- [x] 真实 PG 验证末尾仅 RUNNING、双 worker、begin/接管两个胜出顺序、旧完成拒绝、跨时钟及锁等待；真实 HTTP 验证旧 GET 迟到零重复写。
- [x] 独立 JVM 的 PENDING/CLAIMED_UNWRITTEN 两场景均强杀退出 137，默认扫描重启继续原任务；MR POST=1、MERGE PUT=1，Item/Task/成功 Step 前缀保持。
- [x] API 48/48、普通后端 675 PASS/5 既有 SKIP 在静态整理前各自稳定树通过；仅适配器 SQL/标注整理后的最终树再次组合 76/76，源码前后 hash 一致，重叠不累计。
- [x] 最终静态 20261121-214959：六模块 SpotBugs 0、typecheck PASS、lint 0 errors/8 既有 fixture warnings；首扫 4 项修复或精确说明，TopN 逐项留痕。
- [x] 代码 `3f810c8`、文档 `5441df6` 已分组提交推送；本片固定 `5441df6bdc971953dee390c9470d4786fe0eab44` 的 Full-Link 44911634004 / job 136868521085 为 5/5（2026-11-21 22:09:44，Asia/Shanghai），未借用 C2-A 的 5/5；证据 `.ai/reports/validation/c2-b1/full-link/result.json`。
- 回滚和非目标：停派发、保留事实；不处理任何已有 operation，不宣称任意中断点或完整 UI 旅程可恢复。

#### C2-B2：规范化建分支证明与投影恢复（已交付）

- 依赖与状态：C2-B1 固定 `5441df6` 的远端验收已完成；本片已完成实现、本地门禁、分组推送及固定 cc80036 的 Full-Link 44917254215 5/5 验收。正常创建成功证明的规范化与 APPLIED 建分支投影恢复同片交付。
- 用户价值与范围：只恢复 CREATE_BRANCH/APPLIED 对应原 ENSURE_FEATURE/ENSURE_RELEASE 的本地投影，恢复阶段零网络；回连设计 0/3/4/5，复用原任务、账本、guard 与单步入口。
- [x] 初始 RED 已保留：客户端 55（3 FAIL）、真实 HTTP 9（9 FAIL）、PG 投影 18（3 FAIL）、独立 JVM 1（1 FAIL），均 0 ERROR 且阶段源码稳定。错误 commit.id 被 object.id/commit.sha 覆盖会实际错误 CREATED/APPLIED 并清 guard；正确字段反向受干扰、非 201、规范证明缺失及重启卡住均复现，详见任务记录 15.1。
- [x] 正常 CREATE_BRANCH 与既有 event evidence_json 共用“201 + 完整匹配 name/commit.id”判定，只允许确切 commit.id，其他字段不得替代或覆盖；不改变 GET 分支/Tag 等契约。同时核对 plan.defaultBranch=target.sourceRef、计划分支=target.branch、target.ref 为合法 SHA 且等于 commit.id，不重读当前 ref。
- [x] 以原 operation 的 numeric project 对齐同 Run/仓库已持久身份，原 operation/token/期限不改；新 Step 仅记录当前 worker 处理起止，Task 首次时间与 HTTP 历史时间保留。
- [x] 沿原 RunPort/JPA 增加窄投影责任，由 RunAppService.executeNextTask 实际消费；一个 Run→Task→guard 结果事务复用完整提交，Journal 检查显式 MANDATORY，验证 self-call 不另起事务、flush/clear 后不使用旧实体。
- [x] PG 验证完整领取/实际顺序、双恢复者、旧 owner 两个胜出顺序、UNKNOWN 排除、guard 归属及失败整体回滚；证明不足不续租、不回 PENDING、不放行后继。
- [x] 真实 HTTP/独立 JVM 在完整 APPLIED 后、Task/Step 前硬杀，exit=137；默认扫描重启补原 Task，投影阶段 HTTP=0、分支 POST 总数 1、原 token/events 保持，随后原 Run 完成。
- [x] 同一 backend hash 的组合 189/189、API 48/48、普通后端 700（695 PASS/5 既有 SKIP）及静态门禁完成；六份新鲜 SpotBugs XML 均 0、typecheck PASS、lint 0 errors/8 既有 fixture warnings，TopN 报告 20261121-224714 逐项留痕。
- [x] 分组提交 fe4c006、1386ccc、cc80036 并推送；固定 `cc800362f333418776f4a816e1fa74ee9f08c04f` 的 [Full-Link 44917254215](https://github.com/tongsh6/release-hub/actions/runs/44917254215) 为 5/5，B2 已 DONE。未借用 B1 CI 或旧 UI 214 的结论。
- 回滚与不做项：停派发并保留事实；不新增实体/API/UI/通用恢复平台，不发第二次请求、不迁移或补造旧摘要；MR FOUND、INTENT/SEND/UNKNOWN 和 cleanup/version 不在本片获得恢复授权。

#### C2-B3：创建 MR 完整证明与原检查点恢复（已交付 / DONE）

- 依赖与范围：B2 已完成固定 SHA 验收；仅 CREATE_MR/APPLIED 对应原 ENSURE_MR，纯 DB 恢复历史检查点，再由原 TRY 消费。详细正反例及 provider 契约见 design.md 的 C2-B3。
- [x] 独立正反例、已有职责与实际消费者核对：原 operation.target.sha 已冻结；原 MrEvidence、Task/Journal/guard、completeTask 与 PENDING 后继足以承责，无需新实体或扫描分支。
- [x] 初轮 RED 已保留：客户端 56 项/19 FAIL、workflow 44/2、真实 HTTP 28/13、PG 38/3、独立进程 2/1，全部 0 ERROR/0 SKIP，三阶段及整轮源码 hash 稳定。11 个 HTTP 反例实际错误 CREATED/APPLIED 并释放 guard；另见规范证明缺项与 mergeSha 污染。MR 第二进程正常启动后 35 秒仍未恢复原 ENSURE_MR，分支场景通过。详见本轮记录 16.1 和 `.ai/reports/validation/c2-b3/initial-red-jdk21/result.json`；这不是 GREEN 或交付结论。
- [x] 正常 create、HTTP event 和恢复共用严格规范证明；保留项目原字段、iid/url、分支、sha、opened 和 httpStatus，不从意图补造响应，不混入 readiness；创建检查点 mergeSha 为 null。
- [x] 原 RunPort 投影入口改名并删旧名；Journal 返回既有 MrEvidence；移动原 metadata patch 映射供正常路径与投影共用，保留 null 清除语义和 requestId 来源。
- [x] PG 验证原身份/时间/事件保持、整体回滚、双恢复者和 owner 顺序、完整项目检查点、旧摘要/UNKNOWN/错误证明拒绝；不新建 operation、不重发。
- [x] 独立 JVM 在 CREATE_MR/APPLIED 后、结果提交前 exit=137，重启原 ENSURE_MR 投影阶段 HTTP=0；总 MR POST=1、MERGE PUT=1，原 token/三条事件保持，原 Run 由原 IID/SHA 继续。
- [x] 当前 MR 已关闭或 source 移动且 target 不含原 SHA 时，后继 TRY 停住且无重复写；target 精确包含原 SHA 则保留 ALREADY_MERGED。
- [x] 交叉复审新增 SHA 等值表示反例：应用 2/2 FAIL、HTTP 4/4 FAIL 后，只修原 TRY 检查点和应用成功结果的 SHA 比较；保留原值、意图及解析。新增 6 例先随 371/371 转绿，最终纳入 403/403；365/371 保留为中间轮，详见本轮记录 16.2、16.4。
- [x] 旧夹具失败已按真实契约修正并重跑：最终 11 suites、403/403 定向，API 48/48，普通 738（733 PASS/5 既有 SKIP），均 0 FAIL/ERROR；API/普通 suite 清单匹配，403/API/普通/静态后的 495 个 backend 文件 hash 一致。718 项失败与缺席 20 项历史保留于 16.3，最终结果见 16.4。
- [x] 四类真实 exit=137 后默认 worker 继续；最新 MR 原 Run 投影 HTTP=0、MR POST 总数 1，原 token/三条事件保持。六份新鲜 SpotBugs XML 均 0、typecheck PASS、lint 0 errors/8 既有 fixture warnings；静态报告 20261122-001110，backend 完整源码复审无阻塞。
- [x] 静态报告 20261122-001110 的八项既有 fixture warning 已逐项记录保留理由与覆盖边界，仍为 0 errors/8 warnings；本次 raw 与 B2 字节一致，未关闭规则或宣称告警已修复。
- [x] 后端 16 文件完整纵向切片已提交并推送为 d87dfa87751713b2a8a146bdb8062ef06c9571ce，提交树与 403/48/738/静态验收相同；源码完整复审无阻塞。详见记录 16.5。
- [x] 文档 544983beff74604852c050209e4c8caa93678ef2 已提交推送；固定该完整 SHA 的 [Full-Link 44924378741](https://github.com/tongsh6/release-hub/actions/runs/44924378741) 为 5/5，B3 已 DONE。覆盖为后端 API 与隔离 GitLab，不扩完整 UI 或 Slice4/5 边界；证据见记录 16.6。
- 回滚：停派发并保留事实；MERGE_MR、INTENT/SEND/UNKNOWN、版本/收尾恢复与完整 UI 旅程继续独立追踪，不兼容缺证明的旧摘要。

#### C2-B4：合并 MR 完整证明与末尾原任务结果恢复（已交付 / DONE）

- 依赖与范围：B3 已完成固定 SHA 5/5；本片只处理原 TRY_MERGE 对应 MERGE_MR/APPLIED。完整契约见 design.md 与 Run delta，实际阶段证据见任务记录 17；本地通过不代替固定 SHA 远端验收。
- [x] provider 与持久化两路独立只读核对已收敛；原 Task/Journal/guard、MrEvidence、worker 游标和 completeTask 有实际消费者，无需新实体、表、DTO、API 或框架。
- [x] 官方 v17.11.3、grape-entity 1.0.1 的九文件 producer 基线已保存范围/hash；FF/FF+squash 的合法 merge_commit_sha=null 与缺字段明确区分。
- [x] 独立 RED 已保留：冻结快照 v2 的 adapter 105 项/20 FAIL、Workflow+App 77 PASS、HTTP 34 项/28 FAIL、进程 3 项/1 FAIL；独立 PG 44 项/12 FAIL，旧字段 DB 前置断言补强 5 项/5 FAIL。正确执行阶段均 0 ERROR/SKIP、各自源码稳定；最初编译失败 0 tests 不计业务 RED，不同测试树及重叠用例不混算，详见 17.1。
- [x] 正常 merge、HTTP 规范事件与 Journal 共用 confirmedMergedMr；不加 nullable 分支参数/header，精确分支由既有 workflow/原 claim 各自绑定；target 矛盾即使 APPLIED 也不能业务成功。
- [x] 保留原 CREATED/MR_REUSED 检查点 identity 字节与 disposition，只复用正常 TRY 的 MR_RESULT 四字段 patch；合法 null 真实清旧值，40/64 位等值大小写不全量套用 ENSURE patch。
- [x] 窄扩完整到期 TRY+MERGE_MR/APPLIED 且无 Step 的候选；原 NO_TASK_EVIDENCE 接管不放宽，协议/有界 keyset 保持，锁内核唯一 operation/attempt、token/session/generation、项目、PUT/suffix/SHA、计划与检查点。
- [x] 纯 DB 补历史 MERGED；原 token/lease/首次时间/顺序/op/events 保持，新 Step 与 lastMergeAt 取当前处理 Clock；受控 HTTP fixture 的后来 target 重置/MR 不可读不触发新 GET/PUT。
- [x] PG 验证错误证明/guard/checkpoint/owner UNKNOWN/current Step 拒绝、双恢复者、owner 先后及完整结果事务回滚，绑定与仓库/迭代聚合分别验证。坏页验证为真实 SQL keyset，回滚为外层 TransactionTemplate；具体证据边界见 17.3。
- [x] 五类独立 JVM 强杀重启通过；末尾 MERGE/APPLIED 时无 PENDING，默认 worker 纯 DB 恢复原 TRY/MERGED，投影 HTTP=0、MR POST=1、merge PUT=1，原 token/六条事件保持。
- [x] 首轮 533 项/2 FAIL 保留；仅修两份测试的发现/授权及无序集合断言，生产未改，最终 11 suites、533/533、0 FAIL/ERROR/SKIP。独立源码复审无阻塞；详见 17.2–17.3。
- [x] 同一 495 文件 backend 树 API 48/48、普通 789（784 PASS/5 既有 SKIP）、六份 fresh SpotBugs XML 0、typecheck PASS、lint 0 errors/8 既有 warnings；TopN 八项已逐项说明保留理由，报告 20261122-010845，详见 17.4。
- [x] 后端 13 文件完整纵向切片提交 23950f98f2c7000bc054e74e9a67feb5f892e2dc，与最终 533/48/789/静态验收树一致；已随文档阶段推送，本地提交时点的历史状态见 17.4。
- [x] 文档 be1ccbe61a5155ba5ce8582f884d41dbb385a932 已门禁通过并分阶段推送；固定该完整 SHA 的 Full-Link 44929301318 / job 136922204638 为 SUCCESS、5/5，B4 标记 DONE，详见 17.5。
- 回滚与后续：停派发并保留事实，不撤销 merge；INTENT/SEND/UNKNOWN、cleanup/version 与完整 UI 仍独立排队。

#### C2-B5：原 MERGE 未发送意图的安全续发（已交付 / DONE）

- 依赖与范围：B4 已完成固定 SHA 5/5；B5 仅恢复完整过期 RUNNING TRY 的唯一 MERGE_MR/INTENT、原 attempt sequence 0 且无任何 SEND/自身 Step。独立 RED、实现、本地门禁、分阶段推送及固定后端完整 SHA CI 已完成，事实见记录 18；未发送本地结果限制保留为交付历史，由 B6 独立接续。
- [x] provider 与持久化/调度两路只读审查及独立正反例已收敛；Task 当前 token 与 op/guard 历史 reservation 分责，复用原领取、Journal、workflow、interceptor 与结果事务，无新实体、表、API、DTO 或 dispatcher。
- [x] 有效独立 RED：provider 25/21 FAIL、HTTP 7/7 FAIL、PG 71/17 FAIL、process 3/2 FAIL；均 0 ERROR/SKIP，各冻结阶段 sourceStable，生产为旧 HEAD 4a067a8。首次 provider 的 4 个 Mockito ERROR 与 sourceStable=false 单列，不计有效 RED；详见 18.3。
- [x] 原 claimNextTask 在 Run→Task→guard 锁内核完整资格、原计划/项目/checkpoint/request 和前序，唯一调用 MANDATORY prepareUnsentMergeClaim，以 PESSIMISTIC_FORCE_INCREMENT 即时递增 op version，再同事务 CAS 只换 Task token/lease，CAS 失败抛错回滚；op claim/session/attempt、guard reservation、首次时间/顺序与历史事件不改。
- [x] 持久原 MERGE 三键完整 typed body、固定次序/类型指纹与无 query（空 ? 也拒绝）；布尔 false 与冻结 SHA 保持，自动续发另核精确原计划消息，正常 producer 保留原输入消息；缺失/未知/错型/旧 hash 拒绝零写，无回填兼容。
- [x] 正常与恢复走原路径，begin 返回原 operation ID 且 interceptor 实际消费；sending/finish 显式核 caller fence、seq1 记实际 sender；ack/APPLIED 核同 attempt sender，B2/B3 既有资格不放宽。
- [x] 旧 SEND 先赢禁止接管且迟到实际响应仍可保存；新接管先赢后旧 sending/UNKNOWN complete 均拒绝；SEND 已提交而 HTTP 前中断不得重发；映射保护与历史 observation 仍有效。
- [x] B5 交付边界：ready 时一次 PUT；GET 失败/closed/source B 可令 Task UNKNOWN，但 op INTENT、无 SEND/响应，原 reservation/INTENT_PENDING 保持；ALREADY/BLOCKED 不造 APPLIED/REJECTED，结果事务拒绝回滚并保 RUNNING/INTENT/INTENT_PENDING。当时尚不能本地收口，改变该边界属 B6，不反写 B5 验收。
- [x] 首轮定向 5 suites、108/108、0 FAIL/ERROR/SKIP、sourceStable；PG 输出真实 pg_blocking_pids 等待关系，坏前页/仓内前序/跨仓进展通过。三个独立 JVM 场景分别验证 INTENT 续发一次 PUT、APPLIED 再强杀后第三 JVM 零 HTTP 投影、SEND 强杀后实际三轮扫描零 HTTP；详见 18.5。
- [x] 相关回归按源码核对汇总 13 suites/692 tests、0 FAIL/ERROR/SKIP：受影响 HTTP 119 + PG 209 的 328 项本轮复验，未改的 11 套 364 项保留上一轮通过证据，明确非单次整跑。首轮 691/1 FAIL 与仅两份测试修正历史保留；真实 CAS 返回 0 已独立 RED 1/1 FAIL 后在 PG GREEN 验证版本及全部事实回滚，详见 18.6–18.7。
- [x] 最终本地 API 3 suites/48 PASS；普通 58 suites/816 tests 为 811 PASS、5 既有 SKIP；六份 fresh SpotBugs XML 0、typecheck PASS、lint 0 errors/8 既有 warnings。七份 495 文件 backend 快照与当前源码一致，静态 20261122-021853 的 TopN 八项已逐项说明，重叠测试不累加，详见 18.7。
- [x] 后端 14 文件纵向切片 064282887ae825255360bcf3b04d0377163219aa 已提交并第一阶段推送，逐文件对应最终验收 hash；main/develop 仍为 d4534cc，远端仍恰三分支，证据见 18.8。
- [x] 文档 62d075c36bd96c35d14bc541957787c5173559f8 已 review/commit/push；固定后端 064282887ae825255360bcf3b04d0377163219aa 的 Full-Link 44935216788 / job 136940242878 为 SUCCESS、5/5，B5 已 DONE。CI 绑定对象未扩为文档 SHA，证据见 18.9。
- 回滚：停派发并保留原 Task/op/attempt/事件/guard；不撤销远端写。其他动作的 INTENT、SEND/UNKNOWN、cleanup/version 与完整 UI 不在本片。

#### C2-B6：原 MERGE 未发送意图的本地结果收口（已交付 / DONE）

- 依赖与范围：B5 固定后端 0642828 的 Full-Link 44935216788 已 5/5；仅当前 fence 的原 TRY/MERGE_MR 完整 INTENT，严格唯一原 attempt seq0、无 SEND/Step；收口不要求 lease 再次到期，未过期原 owner 与 B5 到期换权后的当前 owner 都需验证。完整契约见 design 与 Run delta；记录 19.4–19.7 保存独立 RED、实现与本地验收，实现 4e992e9、文档 29ec005 已分阶段推送，固定实现 SHA 的 Full-Link 44940092094 为 5/5，最终证据见记录 19.9。
- [x] 根侧及 provider/持久化两路只读审查收敛：释放依据是无在途写证明和原子终态，BlockReason 白名单不能承担此责任；核实原 ack/helper/结果事务与 Run/Repo UI 的实际消费者。
- [x] 独立旧生产 RED 已保存（PG 41/24 FAIL、HTTP 13/13 FAIL、JVM 3/3 FAIL，均 0 ERROR/SKIP；UI 3/3 FAIL）：ALREADY 0 PUT（contains A 足够、允许 MR 404）；各真实 BLOCKED 含 GET 403；UNKNOWN 0 PUT 但继续阻断队列/重试；ready 仍按 B5 一次 PUT。
- [x] 沿 acknowledgeTask 扩传实际 patch 并前移至同 completeTask 事务的 Step/metadata 之前，复用 validUnsentMerge 的完整 request/hash、原 CREATED/MR_REUSED checkpoint、guard 与 no Step；不新建入口或放宽 helper。
- [x] NOT_SENT 状态与同 attempt seq1 本地事件和 SEND_STARTED 严格互斥；保留原 op ID/attempt/seq0/claim/session/身份，evidence 记录当前 taskId/claimToken/result 与实际 nullable metadata patch，reason 沿 git.blockReason；不新增 GitWriteResult.Outcome、不造 HTTP status/响应/requestId。
- [x] 核结果语义：ALREADY_MERGED 对应 mrDisposition=ALREADY_MERGED；UNKNOWN/BLOCKED 不带成功 mergeSha/disposition，null 沿既有删除语义且不强制异常 patch 补 requestId；拒绝未发送却 MERGED，不扩大 FAILED。
- [x] 真 PG 竞争验证 MERGE SEND 已提交但无 seq2 响应时，包括同 fence UNKNOWN 在内的所有本地 completeTask 整笔拒绝，保留迟到 finish；已有真实 seq2 的 op UNKNOWN 继续允许既有确认；NOT_SENT 先赢时旧/现 worker 迟到 sending/finish/complete 全拒绝；双本地收口至多一个事件/Step。
- [x] stale/缺 typed/旧 hash/错 guard/checkpoint/多事件拒绝；ack 后外层回滚及真实 Task CAS=0 分别验证 op/version/事件/全部结果/guard 同事务回滚。
- [x] GET 403 保持 BLOCKED/PERMISSION_DENIED；unavailable/closed/sourceChanged 保持 Task UNKNOWN/Run NEEDS_REVIEW 与同仓后继、自动/普通重试限制；MR merged 但 target reset 不误判成功；BLOCKED 沿原 FAIL_FAST/CONTINUE 与用户重试复用 MR。
- [x] 旧 expectedVersion 在 NOT_SENT 后失效；新 observe 保留原执行事实及 guard，沿既有行为记录观察审计时间与版本；只清匹配原 reservation，保留其他 guard。Operation.blocked 仍反映 remote 当前任一 guard，覆盖 mapping/baseURL 的 hasActiveGuards 消费者。
- [x] 真实 JVM INTENT 后强杀、默认 worker 到期后本地 0 PUT 收口；回归 B2/B3/B4/B5 以及末尾发现。
- [x] RunDetail 与 RepositoryDetail/Drawer 的原 GitOperationsPanel 显示 NOT_SENT 状态/事件，补 zh/en 标签与组件验收；不扩大为完整 UI 旅程。
- [x] 新定向 57/57、单次相关 13 suites/749、API 48、普通 816（811 PASS、5 既有 SKIP）、前端全量 217 与静态门禁通过；六份 fresh SpotBugs XML 为 0，typecheck PASS、lint 0 errors/8 既有 fixture warnings，TopN 已逐项留痕。
- [x] 根侧 9 文件 fenced review 与两路独立审查无阻断；九份 495 文件 backend 快照及五份 222 文件 frontend 快照一致，local-evidence-audit.json 已核实。
- [x] 实现 `4e992e9c2406586ec23fa7636292e3d48c163118`（9 文件）已 commit/push；与验收源码匹配，main/develop 仍同 d4534cc，证据见记录 19.8。
- [x] 验收文档 `29ec00586be20730e66cf37da12614e6450d240d` 已推送，固定实现完整 SHA 的 [Full-Link 44940092094](https://github.com/tongsh6/release-hub/actions/runs/44940092094) 为 completed/success、5 PASS/0 FAIL；B6 已 DONE，精确 headSha 与日志见记录 19.9。
- 回滚与后续：停派发并保留事实，不重发 NOT_SENT、不撤销远端写；不加 schema 迁移、实体、表、DTO、API、dispatcher 或兼容回填，其他动作及已发送未知结果仍独立验收。

#### C2-B7：原 CREATE_BRANCH 未发送意图的安全接续与本地收口（已验收 / DONE）

- 依赖与范围：B6 的实现 4e992e9、文档 29ec005 已推送，固定实现完整 SHA 的 Full-Link 44940092094 为 5/5。仅 ENSURE_FEATURE、ENSURE_RELEASE 的原 CREATE_BRANCH 未发送意图；详细独立契约见 design/Run delta 与记录 20；四组独立 RED、实现、定向 91/91、单次相关 840/840 和最终本地门禁均已完成，实现 00def779 已推送；固定实现 SHA 的 Full-Link 44945748427 attempt 2 为 5/5，B7 已 DONE；首轮 429 与最终边界见记录 20.8–20.9，验收文档单独收口。
- [x] 根侧及两路只读审查收敛：冻结 SHA A、预检前绑定原数值项目 I、快照非权限、真实结果不硬转 UNKNOWN、APPLIED 响应证明不追加新 hash 门禁。
- [x] 独立 RED 先覆盖两种 branch 动作、same/new owner、40/64 位 SHA、AUTO/NAMED/EXISTING 及无非法 INTENT；不以既有实现/测试作为正确性标准。
- [x] 原 prepare 原位重命名为同一职责并返回 Optional<Operation>；TaskClaim 增加 nullable 已有 Operation，Context 提前绑定原 I，bindProjectId 保留快照；metadata/plan 不改，无新增持久对象、DTO 或查询接口。
- [x] 在现 GitLabGitBranchAdapter 严格规范 method/suffix/branch/ref/sourceRef 与 canonical hash，由 interceptor/Journal 消费；实际 body 仅 branch/ref、字段顺序固定、ref 大小写保留、无 query；不记录 secret。
- [x] begin 原位返回已有 Operation，不保留 String 兼容返回；首次与续发的 HTTP body 一律消费其已核准 typed target。
- [x] 原 sourceRef 前进/删除后仍发送冻结 A，恢复 sourceRef 解析 0；namespace I→J 在现 expected I resolver 拒绝，J branch GET/POST 0，并保留真实 FAILED。
- [x] prepare/begin/sending 实时核当前 DB fence、完整原请求/hash、原 guard、同 attempt 唯一 seq0、无 SEND/Step；快照与候选扫描不承担权限；不新增发送 expectedVersion，只有观察审计版本增长而事实仍合法时可继续。
- [x] 原 ALREADY_EXISTS/FAILED/UNKNOWN 与空/仅 git.projectId/可选 blockReason patch 可原事务 NOT_SENT，project patch 必须 I；无 SEND 的 CREATED 拒绝，合法 target B≠A 可 ALREADY_EXISTS。
- [x] 真实 PG 锁、begin→SEND 竞争、双领取/收口、旧 worker 迟到、真实 CAS=0 和外层回滚；原 op/attempt/seq0/owner/session、顺序及全部结果事实保持。
- [x] SEND_STARTED 无 seq2 拒绝提前 UNKNOWN；真实 seq2 UNKNOWN 保留 guard，SEND 后强杀不自动复发。
- [x] INTENT 强杀后按原操作续发 1 POST；换权 APPLIED 再次强杀后第三 JVM 按真实 seq1 sender 投影，恢复 HTTP=0；仅修原 owner 判断，B2 既有 201/name/SHA 证明不新增 canonical hash 检查或 hash 双轨。
- [x] 后继、普通 retry、UNKNOWN 限制与观察/guard 的相关回归保持，B2–B6 包含于单次 13 suites/840；复用现有 NOT_SENT UI/API，不新增用户执行入口。
- [x] 四组独立测试仅做 API/发送前夹具适配，逐字逆变换与原 RED 一致；根侧 17 文件 review 和两路独立审查无阻断，见 test-contract-preservation.json、source-review.json。
- [x] API 48/48、普通后端 831（826 PASS / 5 既有 SKIP）、六份 fresh SpotBugs XML 全 0、typecheck PASS、lint 0 errors/8 既有 fixture warnings 已通过，TopN 逐项留痕；九份 495 文件 backend 快照一致。前端未改，222 文件与 B6 已验 217 的树一致，本片未重跑该 UI 套件。
- [x] 实现 `00def779f82d4b10b13c547bacda08fd91706fc9` 已分组 commit/push，17 文件与已验源码一致；验收文档单独收口。
- [x] 本片固定完整实现 SHA `00def779f82d4b10b13c547bacda08fd91706fc9` 的 Full-Link 44945748427 attempt 2 已 completed/success、5 PASS/0 FAIL，watchExitCode=0；B7 已 DONE。首轮 Docker 依赖下载 Central 429、业务断言未运行的全过程保留，后端 API/隔离 GitLab 验收不扩大为完整 UI。
- 回滚与后续：停派发并保留事实，不撤销远端分支、不重发 NOT_SENT 或 SEND/UNKNOWN；CREATE_MR 原摘要不具备完整 title/description/源分支删除选项 且含随机 marker，其恢复和其他动作独立验收。

#### C2-B8：正常 CREATE_MR 完整请求的发送证明闭环（DONE）

- 依赖与状态：B7 已 DONE；B8 独立 RED 与本地实现已验，实现 `537588e` 已推送，固定 SHA CI 44972473174 attempt 1 为 5 PASS / 0 FAIL，本片已 DONE。
- [x] 核实正常 ENSURE_MR 和现非 Task cleanup 两条真实消费者、完整请求职责、最强反例及数据/API影响。
- [x] 先独立 RED 覆盖两条正常真实 HTTP/Journal 链；target/hash/marker/实际 body 与 B3响应/原结果消费者一致，taskFence=null 不误拒绝。
- [x] 原 GitLabMergeRequestAdapter.create 四字段 Map 原位补明确 description 空字符串；同类 typed canonical 规则由现 interceptor/Journal 消费，不保留旧四字段入口。
- [x] 严格五字段：source_branch/target_branch/title 非空白字符串（保持原值、不 trim）、description 字符串可为空但不能 null、remove_source_branch 布尔 false；缺/错/多字段拒绝，保持共享 feature 不擅删。
- [x] 入站 reserved marker 前缀即拒绝，只为新鲜 UUID opId 追加唯一独立末行；opId 非空白且无 CR/LF，保持原值、不 trim，recorded marker 精确对应且前缀只出现一次，不静默清洗/去重，合法 Unicode/引号/换行按原值保留。
- [x] target 严格八字段 method/suffix/五正文域/sha，40或64位hex SHA保留字节与大小写；canonical正文固定五字段次序，无query（含空?），hash按design精确定义，不改MERGE。
- [x] begin 仍返回已有 Operation；interceptor核候选 id/action/repo/remote/provider/base/project/target/run/operator/INTENT_RECORDED/null reason，从返回target重建HTTP body；错误返回零SEND/POST。
- [x] Journal.begin/sending核完整typed请求/hash/marker；sending实时核op.reason=null、GITLAB/canonical base/正整数project与remoteKey自洽、真实session/guard。Task另核当前fence、原ENSURE_MR source/target、consistentProject、历史owner、同attempt唯一seq0/无SEND/无自身Step。
- [x] 非Task cleanup同样核完整命令与现session/guard，不套Task专属约束；复用原发送事务，无额外GET、无网络持锁。
- [x] 覆盖begin后当前owner记录破坏、真实PG锁竞争、旧fence/owner及事件/自身Step污染；合法INTENT_PENDING guard不替代完整实体证明。
- [x] Journal admission/sending自身事务失败时仅回滚该未提交事务内的新变化，零非法SEND/POST；sending反例实际经过seq1/guard暂写后抛错，不以前置拒绝冒充回滚。
- [x] 外层业务回滚保留已由REQUIRES_NEW独立提交的INTENT/SEND/响应事实；Task沿既有结果确认/APPLIED投影，非Task沿既有会话收口保持必要guard，不把已知APPLIED改说远端UNKNOWN，不因回滚自动重放。
- [x] prepareUnsentTaskClaim、validUnsentOperation恢复白名单、RunJpa接管、Context resume/NOT_SENT仍关闭CREATE_MR；完整INTENT到期的默认扫描/领取真实PG验证0 token换权/0 SEND/0 Step/0 POST；原owner主动UNKNOWN仍保留INTENT/seq0/guard，不是恢复或NOT_SENT。
- [x] source A→真实响应B仍UNKNOWN/guard保留，不建CREATED检查点、不启动TRY；创建POST无sha-CAS，不把本地sha当外部原子条件。
- [x] 历史B3三字段target/任意hash的APPLIED按原响应证明及现独立进程trace回归，不加完整请求/hash门禁；observer维持marker整行匹配，FOUND不授权。
- [x] 旧 shouldCorrelateRequestWithoutPersistingTokenOrBodyContent 的“标题不存”断言有依据地调整为完整请求可审计；token/认证header/无关正文及响应证据隐私约束保留，不宣称所有旧断言机械迁移，PG曾将原owner主动UNKNOWN错误禁止，已按真实消费者校准并补正控；校准后新独立RED新增块逐字保持，未改到期自动接管负控。
- [x] 核实现targetJson API返回受支持完整命令：当前title固定分支名模板、description空串+marker；查询需认证但无仓库/操作者过滤，不扩RBAC，Panel不渲染targetJson，无新自由正文或UI入口。
- [x] 本地真实HTTP/PG、非Task消费者、既有APPLIED进程回归、相关/API/静态TopN、源码绑定与六文件审阅完成；实现已分组commit/push。
- [x] 固定实现SHA的Full-Link CI 44972473174 attempt 1 为5 PASS/0 FAIL，五个纵向场景日志均PASSED；覆盖后端API与隔离GitLab，不扩大完整UI或Slice4/5既有验收边界。
- 本地证据：独立RED 125例=107 FAIL/18正控PASS；GREEN 125/125；最终相关单次965/965含全部新例、PG368与进程强杀15，API48/48，普通877=872 PASS/5既有SKIP。八份最终backend快照495文件一致；首轮GREEN与最终仅一个旧PG fixture有差异，未宣称各轮同一树。静态20261122-093043六模块SpotBugs0、typecheck PASS、lint0 errors/8旧fixture warnings已逐项留痕。前端217为历史结果，本片未重跑；详见记录21.6–21.11。
- 边界与回滚：既有target_json TEXT承责，无新实体/表/schema/DTO/Port/执行器/缓存/用户入口；Operation不加hash/session/fence字段，不加expectedVersion发送门禁。停派发并保留事实，不清数据、不重发未知，不扩大完整UI。

#### C2-B9：原 CREATE_MR INTENT 的安全接续与本地收口（DONE）

- 依赖与状态：B8 已 DONE；B9定位基线为 `8cfc294c58f0ae53817a44565801936888a03bb1`。已完成核心独立103/103、排序4/4及最终单次相关1078/1078、API48/48、普通918（913 PASS / 5既有SKIP）、静态与最终源码审计；113新例包含在1078内。实现 `666acdc7a60c0922db2f05713cbfefd23eed8265` 已推送，固定SHA Full-Link 44978661003attempt 1为5 PASS / 0 FAIL，B9已DONE。首次1074失败与校准保留于记录22.7–22.10，最终本地证据、推送与固定SHA终态见22.11–22.14。
- [x] 独立定义原I/A命令、有限接续资格、当前发送权、真实本地结果及最强反例；查重后复用现Operation/TaskClaim/Context/Journal/SourceRevision/workflow和原结果消费者，不新增恢复执行器。
- [x] 冻结workflow独立RED：仅在8cfc294导出树给ensure声明加第4个nullable SourceRevision参数、原逻辑忽略参数、两个现调用补null；保存三处无行为API适配patch和源码绑定。原工作区生产不因此修改，无兼容overload；不把该树称纯测试overlay，不把编译失败算产品RED。
- [x] 在原生产冻结树定义并验证真实HTTP与PG独立RED；先固定正反例和定位依据再核trace，保留实际运行和必要校准事实。
- [x] 仅原ENSURE_MR/CREATE_MR完整八字段typed target/hash/唯一marker满足资格：同Task恰一op、同attempt唯一seq0、INTENT/null reason、无SEND/自身Step、原I/计划source-target/consistentProject、历史owner/session/token/generation与匹配INTENT_PENDING guard。
- [x] 复用Run→Task→remote锁序及当前fence；lease只约束换权。接管仅换Task当前token/lease与op观察version，原id/attempt/session/claimToken/generation/seq0/首次时间/实际顺序保持，seq1记录真实新sender，finish/ack/APPLIED投影核seq1。
- [x] adapter在任何业务预检前固定原I，复用SourceRevision(I,A)传现workflow.ensure；两个生产调用者直接迁移，fresh/cleanup传null，无旧路径或新Port。
- [x] prepared先判contains(target,A)：已含A即允许无IID/URL的ALREADY_MERGED，零source/list/detail/POST；未知/权限结果沿既有分类，不伪造成contains=false。
- [x] contains=false后读取完整opened列表；唯一候选详情I/I/I、source/target/A、OPENED才MR_REUSED，source已B仍可重用A的MR，原TRY独立重读并以A约束merge。
- [x] 只有完整opened列表确空才readSource；真实I/A才原POST。多候选/不完整/foreign详情、B/删除/读取未知均不创建，权限保持既有blocked，不挑选第一条或把失败当空列表。
- [x] read原位先核原始三个数值项目身份再映射现MergeRequest；prepared失败context保原I/source/target/A，不借B/foreign的iid/url/mergeSha/disposition。SHA比较ignoreCase，新prepared检查点保原A字面；fresh/cleanup mergeSha补充和B3已SEND的部分IID诊断保留。
- [x] prepared interceptor复用原id/body/marker/hash，从begin返回Operation重建真实五字段POST；snapshot只给值，Journal实时核当前发送权和完整证明，网络在锁外，GET→POST响应B仍UNKNOWN/guard/无TRY。
- [x] 完整noSEND的MR_REUSED/ALREADY_MERGED/FAILED/MERGE_BLOCKED/UNKNOWN按真实结果形状NOT_SENT；允许idless ALREADY_MERGED和UNKNOWN最小blockReason patch，已提供身份不得矛盾，拒绝CREATED或非空git.requestId。
- [x] NOT_SENT event、Task/Step/metadata、队列/Run及匹配guard释放在原结果事务一起提交；真实CAS=0、实际写入后抛错和sending竞争验证原子性，UNKNOWN继续阻断后继与普通retry。ENSURE_MR仅保持前序binding事实，不新增binding写入，不声称验证了本动作的binding写入回滚。
- [x] SEND无seq2不得提前本地UNKNOWN；真实seq2 UNKNOWN保留guard。REQUIRES_NEW已提交事实不随外层结果回滚消失，不把已知APPLIED改成远端UNKNOWN，不自动重放。
- [x] 历史B3三字段target/任意hash APPLIED不增加完整请求/hash门禁；原ignoreCase校验后返回响应SHA字面的投影保持，新sender仍核seq1。B8非Task正常写继续回归，不扩大非Task接管。
- [x] 明确迁移B8两项阶段负控：合法完整INTENT到期成为安全接管正例；原owner显式noSEND UNKNOWN成为NOT_SENT。损坏/旧摘要/SEND/UNKNOWN禁区及其断言保持，历史B8结果不改写。
- [x] 真实多JVM验证INTENT后强杀→新owner按原A/body/marker SEND→APPLIED后再强杀→第三JVM纯DB投影，MR POST全程总数1；本地NOT_SENT的真实HTTP计数0，原Task检查点/TRY/队列继续消费。
- [x] 独立RED→实现GREEN→相关/API/普通后端/静态TopN、源码绑定与审阅已完成；实现13个backend文件独立commit/push，提交源码与最终验证一致。
- [x] 固定实现完整SHA的Full-Link 44978661003 attempt 1已completed/success，五个slice全部PASSED、0 FAIL；B9已DONE，证据见22.14，不扩大完整UI或Slice4/5原验收边界。
- 范围与回滚：无新entity/table/schema/DTO/Port/executor/cache/UI或自由正文入口；停派发并保留原事实，不清数据、不重发SEND未知。S3整体、后续SEND/UNKNOWN/其他动作与完整UI继续独立验收，SA-002顺序不变。

##### B9验收追加：步骤顺序（本地已验 / 首次失败保留）

- 已发生：首次13 suites相关1074为1073 PASS/1 FAIL、21多JVM全PASS，暴露steps读取顺序缺陷；四个排序用例在冻结8cfc294生产上全部断言RED。一次零用例的lambda遮蔽编译失败保留，单行参数名修正后4/4 GREEN，最终单次1078/1078包含这四例。完整历史与本地验收见记录22.7–22.12。
- [x] 复用现RunStepJpaEmbeddable.taskId与RunTask.taskOrder，核run/targetType=RUN_ITEM/targetId/actionType后确定关联顺序；V10无run_step.id，不增实体/schema/字段/DTO/API。
- [x] 源码复核：toDomains每批Runs最多一次findAllById读取关联Task，无关联时零额外query；局部Map不缓存，completeTask复用已有ordered列表，不逐Item/Step查库。四个排序PG用例没有SQL计数或completeTask返回值专属断言，不把此项写成它们的运行覆盖。
- [x] 核准关联步骤按taskOrder在前，其他步骤保留在后按startAt/endAt退化；覆盖外来身份、混合比较器非传递和无Task同时间戳无法还原真实先后的边界。
- [x] 四个独立排序正反例先RED后GREEN：在真实PG加载事实之上控制内存集合排列，分别核findById/findAll/findPaged完整steps顺序及末项；BlockBoard最后step、RunDrawer/RunDetail和原结果返回经源码核实为实际消费者。保留1074失败，不声称数据库物理返回了控制排列或本片运行了UI套件。

#### C2-B10：MERGE_MR 精确只读核对证据（DONE）

- 依赖与状态：B9已DONE；定位基线`d51a0b7efd20cdcf005aa165330cf3de6312de05`，B9实现`666acdc7`的CI44978661003首轮5/5。B10独立契约见design/spec与记录23.1–23.6；冻结RED为27=23 FAIL/4正控，独立GREEN27/27、UI基线4/4及完整前端218/218通过，实际证据见23.7–23.8。单次相关1123/API48/普通942（937 PASS+5旧SKIP）、静态和最终源码审计均通过；实现8dcca5bf已推送，固定SHA Full-Link44982102777 attempt 1为5 PASS/0 FAIL，B10已DONE，证据见23.9–23.11。
- [x] 先定义I/IID/A观察目标、原PUT/原计划因果边界、正反例与实际消费者；查重复用GitRemoteEvidenceAdapter、confirmedMergedMr、reconcile/Observation和现UI，不新增实体或接口。
- [x] 原typed PUT、严格`/merge_requests/[1-9][0-9]*/merge`与合法文本A限定locator；非法定位INCOMPLETE且MR GET=0，namespace I→J先阻止J业务GET。
- [x] 以实际GET status及raw正文复用完整B4证明，历史三字段/当前五字段target同一路径；不增加完整请求hash门禁、旧正文补全或兼容层。
- [x] absent/null/非object为INCOMPLETE；HTTP200 object含`{}`但完整证明失败为MISMATCH，201完整也MISMATCH；完整200才FOUND，403/404/不可用沿现分类。
- [x] 只保留十个白名单scalar/null字段原JSON类型；缺失不补null，嵌套省略，不记录token/header/任意正文，API/UI直接消费现evidenceJson。
- [x] FOUND只证明I/IID/A对象当前merged，实际source/target变化原样保存；不解析merge_commit_message推导计划，不扩Operation/Task关联接口。
- [x] 观察只新增Observation并更新op.version/updatedAt；Task/Step/Run/metadata/guard/events/attempt及原执行状态保持，UNKNOWN后FOUND仍UNKNOWN/NEEDS_REVIEW/guard，不完成或重放。
- [x] 真实Spring/HTTP/PG验证两种顺序：observe先commit后原seq1 sender仍finish；finish先commit后旧GET观察STALE且零写，网络不持账本锁。
- [x] 根侧冻结d51a0b7生产取得27例独立RED（23 FAIL/4正控），0 ERROR/SKIP、仅测试overlay、无签名seam；新增adapter24仅3固定+15响应+6 locator，既有18回归另计，namespace漂移/通用错误的源码边界不冒称新24覆盖。
- [x] 独立GREEN27/27及源码审查通过；UI基线原3+新1为4/4，完整前端218/218含coverage通过；27例身份逐项一致、旧测试行保留、无校准。新三HTTP不含same-key零额外GET断言，幂等沿既有回归/源码证明。
- [x] 完整本地门禁与交付源码绑定通过：相关单次14 suites1123/1123（含全部27新增）、API48/48、普通942（937 PASS+5既有SKIP）、前端218/218及静态TopN；10份backend495、6份frontend222快照分别一致，8页完整review代码绑定及独立审阅无阻断。
- [x] 实现4文件已单独commit/push为 `8dcca5bf8265a3a1f36c16a14f0e3b8b0b35652d`，父d51a0b7，远端已核相同源码；main/develop仍同d4534ccc，验收文档单独收口。
- [x] 固定实现完整SHA的Full-Link44982102777 attempt 1已completed/success，14步骤成功、五个slice PASSED/0 FAIL；B10出队。CI只覆盖已有backend API与隔离GitLab五切片，新观察的真实远端/完整UI旅程仍未随本片验收。
- 范围与回滚：无新entity/table/schema/DTO/Port/执行器/用户入口；未验收前停止交付，缺陷以修复提交处理；保留已有Observation/执行/guard事实，不清数据或重发未知请求。后续SEND/UNKNOWN受证据约束恢复继续QUEUED，之后仍按SA-002既定顺序。

#### C2-B11：GitLab MR读取响应状态证明（DONE）

- 依赖与状态：B10已DONE；基线62f21035a8631dfabeffb2384eb1120bde4bd4ff。独立契约、实际RED/GREEN、本地验收与实现推送见记录24；固定SHA Full-Link44984865795 attempt 1已completed/success，5 PASS/0 FAIL，B11已DONE并出队，终态见24.9。
- [x] 查重确认getMap→readSource/read、readPages→find/contains以及既有workflow实际消费；先定义正反例和最强后页反例，不新增实体、接口、target快照或权限。
- [x] 冻结原生产取得独立RED：adapter新增25+旧5选择30=16 FAIL/14正控，HTTP3=3 FAIL，合计33选择例（28新增+5旧正控）=19 FAIL/14正控，0 ERROR/SKIP。HTTP两处快照SQL的id排序修正为task_id后复跑，合同/断言不变，首轮保留。
- [x] 只在getMap/readPages两个GET出口先核actual status=200；未抛异常而返回非200为INCOMPLETE/null；既有正文证明、401/403/404/network分类保持。
- [x] 后页异常或非200丢弃累计匹配并停止后续请求；真实HTTP核fresh/prepared ENSURE_MR和TRY_MERGE不因不完整证明产生错误结果或写请求。
- [x] 独立GREEN33/33、13.7s，backend495前后稳定；RED/GREEN身份审计、旧测试行保留及唯一fixture修正边界已核。
- [x] 完整本地门禁通过：单次14 suites相关1151/1151、API48/48、普通967（962 PASS+5原SKIP），静态TopN、源码绑定与独立审阅通过；28新增包含于1151，10份backend495快照一致，前端222与B10一致。
- [x] 实现3文件单独commit/push为35ca21366ec9124fe9c4d6d2ef162a09c13709fb，提交源码与最终验证一致；远端feature已核相同，main/develop仍同d4534ccc，验收文档单独收口。
- [x] 固定完整实现SHA的Full-Link44984865795 attempt 1已completed/success，14步骤成功、5 PASS/0 FAIL，B11出队；CI边界仍为既有backend API与隔离GitLab五切片。
- 前端本片无改动；B10的218仅历史沿用，不重跑组件套件，新跑typecheck/lint。未验收前不交付，缺陷以修复提交处理；保留执行/观察/guard事实，不清数据、不重发未知请求，SEND/UNKNOWN后续恢复继续QUEUED。

#### C2-B12：无自身写意图任务的失败与重试闭环（DONE）

- 依赖与状态：B11已DONE，洁净基线59f6311d811616d9b990e714d60ce70274dc749b。独立契约见design/spec及记录25.1–25.4；两组独立RED后，独立GREEN23/23、相关1174/1174、API补证后48/48、普通967通过；事实与校准见25.5–25.7，静态/最终源码总审计已通过（25.8），实现7042f19278ff8d8bfa102a8f27d0c1f1b949539d已推送，固定SHA Full-Link44989857158 attempt 1已completed/success，5 PASS/0 FAIL，本片已DONE并出队（25.10）。
- [x] 当前WINDOW协议、原Run→Task锁与当前token下，首次无自身operation且无自身Step的UNKNOWN归FAILED；非UNKNOWN保持，任意自身operation及历史UNKNOWN沿原协议。
- [x] 原ack返回现RunItemResult供唯一completeTask消费者贯穿Step/Item/Task/skip/binding/Run，保存message、metadata含未知原因、原时间与身份；不虚构写记录或成功binding。
- [x] 职责按本Task限定，其他Task APPLIED/别仓guard不改变当前转换，已有Step反例通过；原token与当前协议核验沿lockTask保持（源码复核）。
- [x] 真实PG双向begin/result锁竞争及CAS/外层回滚：结果commit后的晚begin拒绝，结果rollback后的晚begin可合法继续，独立历史事实保持。
- [x] 真实HTTP验证现新Run retry：ENSURE失败后新读B，TRY失败后继承I/IID/A，成功前缀不重写；历史UNKNOWN与真实write UNKNOWN仍受保护。
- [x] 冻结59f6311生产取得独立RED：PG20=9 FAIL/11正控PASS（15.4s），HTTP3=2 FAIL/1正控PASS（15.7s），均0 ERROR/SKIP、源码稳定、仅新测试overlay、无生产签名seam。
- [x] 新RED后旧校准边界审计通过：HTTP5方法仅12行期望，PG5方法六处编辑+23/-1补真实write UNKNOWN及独立project；新增285/502行块与RED逐字节一致，patch保留。旧例已纳入单次1174相关回归通过。
- [x] 三生产文件实现经独立只读审查无阻断；独立GREEN23/23、19.1s，0 FAIL/ERROR/SKIP，495源码前后稳定。
- [x] 单次14 suites相关1174/1174、API复验48/48、普通967（962 PASS+5既有SKIP）通过；API首轮47 PASS/1 FAIL保留，RunPlanApiIT仅补真实Journal写未知fixture，原语句/断言及settings隔离经独立审计保持。
- [x] 静态TopN、最终跨阶段源码审计及六代码文件审阅通过：六fresh XML 0/typecheck PASS/lint 0 errors+8旧warnings；10份快照后6份495全同、前4份仅未选中API fixture不同，其余494一致；前端222与B11相同，218仅B10历史。
- [x] 六代码文件已单独commit/push为7042f19278ff8d8bfa102a8f27d0c1f1b949539d，提交匹配最终495/222验证树；远端feature已核相同、main/develop仍同d4534ccc，验收文档单独收口。
- [x] 固定完整实现SHA的Full-Link44989857158 attempt 1已completed/success，14步骤成功、5 PASS/0 FAIL；B12已出队，CI与源码/历史边界见25.10。
- 范围与回滚：不新增DTO、Port方法、状态、entity/schema或前端入口；现UI失败重试判定为源码消费者，非本片已点击验收。保留历史与guard，不清数据或重发未知，SEND/UNKNOWN恢复继续QUEUED。

#### C2-B13：项目身份响应证明统一（已完成 / DONE）

- 依赖与状态：B12已DONE；洁净基线73ea437dae8263e4e73de79d0454f5a94d2fc8c1含backend495/frontend222。事前合同与责任查重见记录26.1–26.4；独立RED已完成，70选择例为21 FAIL/49 PASS，测试契约保留审计通过。独立GREEN与本片最终验收通过，完整证据见26.5–26.10。
- [x] 独立定义actual200/object/positive integral JSON id无损String证明、三入口真实消费者、原政策与long消费边界；复用现Resolver静态纯函数，不整体接入Resolver流程。
- [x] 冻结生产仅叠加独立测试取得行为RED：adapter62=17 FAIL/45 PASS、HTTP8=4 FAIL/4 PASS，合计70（58新+12旧）为21 FAIL/49 PASS；0 ERROR/SKIP、生产稳定且无签名seam，旧断言保留审计通过，见26.5。
- [x] 三入口接入纯证明：正常非200、坏形状/类型、原I不符零后继；第三入口使用JsonNode且只在long消费处核上界，不丢失大整数或接受文本/小数。
- [x] 保持各自URI/configured base/clone/repoId/provider/token政策；Evidence正常非200为INCOMPLETE、200坏id/不符为MISMATCH，实际HTTP/解析/传输异常保持；第三入口正常不可信/越界使用GIT_OP_004，missingSettings及真实异常仍沿原政策。
- [x] 真实Spring/HTTP/PG验证写入口零新operation/SEND/写请求，以及reconcile的Observation/version/updatedAt变化与业务身份/状态/events/guard、Run/Task/Step保留；FOUND不扩恢复权。
- [x] 独立70/70、相关1239/1239（含该70例）、API48/48、普通1019（1014 PASS/5既有SKIP）及真实静态同树通过；门禁假PASS已纠正并以真实退出码修复，隔离9/9，最终扫描105114四项0/六fresh XML0/8旧warnings逐项处理。
- [x] 业务4dbe2c6、门禁ddcec2e已分组提交推送；固定完整ddcec2ea6520ae7b347e13f9a2a28d1fae68bb67的Full-Link45102738088首轮5 PASS/0 FAIL，B13标DONE；main/develop同d4534ccc保持。
- 影响与处置：无新entity/DTO/Port/schema/API字段/UI入口、额外GET或网络持锁；metadata、其他业务GET、SEND/UNKNOWN及完整UI另验。未验收停止交付，缺陷以修复提交处理，保留历史执行/观察/guard，不清数据或重发未知请求。

#### C2-B14：原 MERGE_MR 已持久拒绝响应的结果投影（当前子片 / HEAD）

- 依赖与状态：B13已DONE；当前仅完成只读调用链核实、方案反证与事前契约，B14测试未落盘、未运行，生产未修改。开工时固定完整基线SHA；不以B13验证代替本片证据。
- [x] 区分完整原同步拒绝、已有终态UNKNOWN历史、SEND无响应三个子集，选择前者MERGE_MR；职责、边界与独立11组验收见design及记录27。
- [ ] 在冻结生产上先完成独立行为RED；先冻结权限/审批等拒绝、HTTP_409及NO_COMMITS的独立业务预期，再分别核正常与恢复路径；覆盖typed状态/reason、原sender/attempt/请求/guard及Task资格，不以现函数输出作oracle。
- [ ] 复用现持久候选发现与锁内投影，接入无PENDING末尾REJECTED TRY；原位复用或更名既有入口，不新增平行恢复服务/DTO/实体/表。
- [ ] 正常执行与恢复共用唯一failedWrite/Run结果/metadata映射，经completeTask/acknowledgeTask同事务补原结果；恢复HTTP=0，不换token/attempt或重写原事件，NO_COMMITS形成UNKNOWN Task/Item/Step及NEEDS_REVIEW Run、finishedAt为空，普通retry继续拒绝。
- [ ] 两投影者/原sender竞争、原seq2保留的结果事务回滚、默认扫描和同仓顺序/跨仓推进、真实独立JVM在REJECTED后强杀重启通过；现Run查询、后继策略及手动retry受理有真实出口；无seq2迟到finish及APPLIED/UNKNOWN保护正控保持。
- [ ] 同批GREEN、相关/API/普通回归、静态TopN、源码绑定与独立审阅后，分组提交推送并核固定完整实现SHA CI，再标DONE。
- 回滚与边界：不清历史、不释放未证明的guard、不重发未知请求；创建分支/创建MR的REJECTED、终态UNKNOWN更正、真正SEND/UNKNOWN恢复及完整UI仍独立排队。

#### C2-B 后续：SEND/UNKNOWN 核对与恢复（QUEUED）

- [ ] UNKNOWN/NEEDS_REVIEW 的精确只读核对、受证据约束的 resume；无 force 绕过或任意 FOUND 自动释放 guard。
- [ ] SEND_STARTED 前后硬中断、外部成功但业务提交失败、租约过期、旧 worker 延迟发送/返回与观察版本竞争；不能把数据库 fencing 当作 Git fencing。

## S4：版本更新与收尾恢复

- 蓝图归属：第 6、8 节的其他动作。
- 用户价值：部分文件写回、合并、tag、归档和 pipeline 的进度与未知结果可核对。
- 涉及层：各执行策略、状态机、API/UI、Tests。
- 依赖：S3；分动作启用，不能用一个开关宣称全部安全。
- 回滚：停用该动作类型的新受理，保留查询及原密钥；未知外部结果继续阻断。
- [ ] Maven/Gradle 文件步骤、内容哈希、提交前置条件和部分成功阻断规则。
- [x] 关闭窗口与收尾意图同事务，业务 CLOSED 与收尾状态分开展示（B2 已交付）；任务化收尾恢复仍属于本节未完成范围。
- [ ] tag、归档创建/删除、pipeline、迭代归档分别验收；无安全自动恢复证据的动作保持人工复核。
- [ ] 取消与补偿计划、历史 Run 受控再计划、凭据和错误脱敏通过验收。
- 后续：S5 完成操作手册、性能和受控业务验证。

## S5：受控发布与运维交付

- 蓝图归属：第 10–11 节。
- 用户价值：恢复能力能在真实运维中使用并安全回退。
- 依赖：S4；真实 GitLab 资源仍需使用明确授权的隔离范围。
- [ ] 硬中断矩阵、远端超时、分页/权限错误、租约过期和旧 worker 恢复演练。
- [ ] 用户从页面触发、观察、核对和继续的完整旅程及 GitLab 实物证据。
- [ ] 排空旧写请求、协议切换、回退、数据库备份恢复后的外部状态重核对。
- [ ] 活跃/未知/待复核任务指标、告警出口和人工操作手册；不引入新的通知平台。
- [ ] 顺序执行基线与多仓队列性能测量；未验证前不扩大并发。

## 放行原则

每个切片的行为、层级、测试、架构、性能、文档和工作区检查均有结果才能出队。不满足条件时记录具体缺口，不得将测试对象模拟结果称作真实 GitLab 或进程崩溃验收。

每个实施切片都必须保留 RED/GREEN、静态扫描 TopN 处理和复扫证据；本轮的扫描不能替代未来实现后的检查。

## S1 交付复盘（2026-10-08）

身份、API、持久化、页面与目标测试闭环完成；V34 无历史数据回填。生产 Flyway 和 local 自建 schema 均已验证，预览无 Git 调用。后端扫描发现的请求集合暴露已修复并复验。真实前端类型检查发现 44 个存量错误（HEAD 基线 45 个，新增 0），此前根配置检查为空集合的结论已纠正；此存量门禁缺口单独追踪，不把本轮类型检查标为全量通过。详见 `tasks/records/2026-10-08-sa-013-s1-run-plan-preview.md`。S2–S5 未实现、未批准。

同日后续 SA-018 已完成真实门禁修复，44 项存量错误清零；见 `tasks/records/2026-10-08-sa-018-real-typecheck.md`。历史复盘保留当时结论。
