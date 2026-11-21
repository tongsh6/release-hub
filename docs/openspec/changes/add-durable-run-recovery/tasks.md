# 实施切片与验收追踪

完整蓝图：[design.md](design.md)。S0、S1、S2 已完成；2026-11-21 用户授权继续研发、必要重构和分阶段推送。S3-A/B1、S3-B2、S3-C1、S3-C2-A、S3-C2-B1 已交付；C1 固定 `e5094ba` 的 Full-Link 44899945002、C2-A 固定 `1761572` 的 Full-Link 44905843754、C2-B1 固定 `5441df6` 的 Full-Link 44911634004 均为 5/5。C2-B1 代码 `3f810c8`、文档 `5441df6` 已分组提交推送。当前唯一队首为 C2-B2，建分支成功证明与 APPLIED 投影恢复已实现并通过本地门禁，待分组推送及本片固定 SHA CI；MR、INTENT/SEND/UNKNOWN 等恢复仍未交付，C2 整体尚未完成。

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

本地验收：API/PG 67/67、真实 HTTP 29/29、独立进程 1/1、后补 worker 7/7，普通后端 672 PASS/5 既有 SKIP，前端 214/214；重叠范围不累计。静态 `20261121-203554` 及 TopN 通过。四组提交已推送，固定 `1761572bca6c149aecba78d26a7b25cb98b5251a` 的 Full-Link 44905843754 为 5/5（2026-11-21），未以 C1 5/5 代替；最后 E2E fixture 契约补验 16/16。C2-A、C2-B1 均已出队，当前唯一队首为 C2-B2。

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

#### C2-B2：规范化建分支证明与投影恢复（当前子片 / HEAD）

- 依赖与状态：C2-B1 固定 `5441df6` 的远端验收已完成；本片为当前唯一 HEAD，实现与本地门禁已通过，待分组推送及本片固定 SHA CI。正常创建成功证明的规范化与 APPLIED 建分支投影恢复同片交付。
- 用户价值与范围：只恢复 CREATE_BRANCH/APPLIED 对应原 ENSURE_FEATURE/ENSURE_RELEASE 的本地投影，恢复阶段零网络；回连设计 0/3/4/5，复用原任务、账本、guard 与单步入口。
- [x] 初始 RED 已保留：客户端 55（3 FAIL）、真实 HTTP 9（9 FAIL）、PG 投影 18（3 FAIL）、独立 JVM 1（1 FAIL），均 0 ERROR 且阶段源码稳定。错误 commit.id 被 object.id/commit.sha 覆盖会实际错误 CREATED/APPLIED 并清 guard；正确字段反向受干扰、非 201、规范证明缺失及重启卡住均复现，详见任务记录 15.1。
- [x] 正常 CREATE_BRANCH 与既有 event evidence_json 共用“201 + 完整匹配 name/commit.id”判定，只允许确切 commit.id，其他字段不得替代或覆盖；不改变 GET 分支/Tag 等契约。同时核对 plan.defaultBranch=target.sourceRef、计划分支=target.branch、target.ref 为合法 SHA 且等于 commit.id，不重读当前 ref。
- [x] 以原 operation 的 numeric project 对齐同 Run/仓库已持久身份，原 operation/token/期限不改；新 Step 仅记录当前 worker 处理起止，Task 首次时间与 HTTP 历史时间保留。
- [x] 沿原 RunPort/JPA 增加窄投影责任，由 RunAppService.executeNextTask 实际消费；一个 Run→Task→guard 结果事务复用完整提交，Journal 检查显式 MANDATORY，验证 self-call 不另起事务、flush/clear 后不使用旧实体。
- [x] PG 验证完整领取/实际顺序、双恢复者、旧 owner 两个胜出顺序、UNKNOWN 排除、guard 归属及失败整体回滚；证明不足不续租、不回 PENDING、不放行后继。
- [x] 真实 HTTP/独立 JVM 在完整 APPLIED 后、Task/Step 前硬杀，exit=137；默认扫描重启补原 Task，投影阶段 HTTP=0、分支 POST 总数 1、原 token/events 保持，随后原 Run 完成。
- [x] 同一 backend hash 的组合 189/189、API 48/48、普通后端 700（695 PASS/5 既有 SKIP）及静态门禁完成；六份新鲜 SpotBugs XML 均 0、typecheck PASS、lint 0 errors/8 既有 fixture warnings，TopN 报告 20261121-224714 逐项留痕。
- [ ] 分组提交、阶段推送及本片固定 SHA Full-Link CI；完成后再移动 HEAD，不借用 B1 5/5 或旧 UI 214 的结论。
- 回滚与不做项：停派发并保留事实；不新增实体/API/UI/通用恢复平台，不发第二次请求、不迁移或补造旧摘要；MR FOUND、INTENT/SEND/UNKNOWN 和 cleanup/version 不在本片获得恢复授权。

#### C2-B 后续：已有写意图与外部证据

- [ ] 租约与代次具有实际接管消费者；历史 HTTP 事实归属和当前发送/投影权分开，不能旋转 token 后丢失旧操作观察或迟到响应。
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
