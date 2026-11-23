# C2-B15：MERGE_MR 同步 200 证据不足的 UNKNOWN 审计式恢复

> 状态：事前契约，尚未实现、尚未运行行为测试。2026-11-23 从基线 `13f637a575bef058b0b743cfd611dec85543d7b8` 完成源码核查与独立反例审查。执行顺序以[唯一路线图](../../../execution-roadmap.md)为准。
> 归属：[SA-013](../../../requirements/in-progress/SA-013-发布编排中断恢复.md) / [完整恢复蓝图](design.md) / [实施清单](tasks.md) / [Run delta](specs/run/spec.md)。本文件集中维护 B15 契约，不建立第二套任务队列。

## 1. 要交付的用户结果

原合并请求已经完整收到同步 HTTP 200，但返回正文不足以证明计划完成，任务因此显示 UNKNOWN。用户在原 Run 核对证据后，能够确认该次计划已经满足：系统保留最初 UNKNOWN 记录，追加有来源的解决事实，将当前有效结果设为 ALREADY_MERGED，并按真实剩余任务重新汇总 Run。恢复过程中不再发送建分支、建 MR 或合并请求。

主要验收对象是**已经提交 UNKNOWN Task/Step 的真实用户路径**。同时覆盖同步 200 已保存、原 Task 仍 RUNNING 且没有自身 Step 的中断窗口；它们共用证明，不以只补这个中断窗口宣称完整 B15。

原来被跳过的同仓后继仍保持 SKIPPED。用户需要继续时，使用现有 retry 选择合格的跳过项；该入口仍检查源 Run、原计划和当前受理条件。此恢复操作只确认所选执行项，不自动重启整仓。

## 2. 按事实拆分，不能只看 UNKNOWN 标签

| 原持久事实 | Task 历史 | 责任与本轮选择 |
|---|---|---|
| 完整 APPLIED 原响应，尚无本地结果 | RUNNING / 无自身 Step | B2–B4 已有纯 DB 投影；保持现有契约 |
| MERGE_MR 完整 REJECTED 原响应 | RUNNING / 无自身 Step | B14 已交付；NO_COMMITS 的业务 UNKNOWN 不属于本片 |
| MERGE_MR 原 seq2 UNKNOWN，证据恰为 typed `{"httpStatus":200}` | UNKNOWN / 唯一 UNKNOWN Step，或合格 RUNNING / 无 Step | **B15**：证明原同步请求已返回，再补业务证据并审计式收口 |
| 原 seq2 UNKNOWN，但证据为 {}、408、5xx、202、其他状态或缺少可信发送证明 | 任意 | I/O/远端执行仍未确认；继续保留保护，不进入 B15 |
| SEND_STARTED，没有原 seq2 | 任意 | 原 sender 仍有迟到 finish 责任；不提前完成、不换权重发 |
| Operation NEEDS_REVIEW、已有 LOCAL_RESULT_UNCONFIRMED 等额外历史 | 任意 | 另行定义来源与更正契约，不借 B15 扩权 |
| CREATE_MR 完整 REJECTED | RUNNING / 无 Step | 独立剩余缺口；可共享正常拒绝映射，但本片不机械扩展 |
| CREATE_BRANCH 完整 REJECTED | RUNNING / 无 Step | 拒绝后的真实分支读取会决定 ALREADY_EXISTS/FAILED，不能照搬零 HTTP 拒绝投影 |

动作、原响应与 Task 历史是三个独立维度。FOUND、租约到期、分支存在、同名 MR 或一个字符串原因，都不能单独授予完成、重发或释放 guard 的资格。

## 3. 原请求已结束的证明

### 3.1 ReleaseHub 原生产者

`GitWriteHttpInterceptor` 先完整读取原响应，再写入整数 httpStatus。连接、读取或响应超限异常保存 {}；完整 MERGE 200 正文证明不足保存 `{"httpStatus":200}`。这两类都使用 RESPONSE_UNCONFIRMED，故 reason 不能代替响应证明。

恢复必须重新核准：

- 当前 WINDOW 协议的原 TRY_MERGE、Run/Item/repository/operator 归属、冻结计划、原 MR 检查点及实际执行顺序。
- 唯一自身 MERGE_MR operation，状态 UNKNOWN；同 attempt 的 seq0 INTENT_RECORDED、seq1 SEND_STARTED、seq2 UNKNOWN 完整且唯一；原 sender 与当前 Task 领取事实相符。
- 原 seq2 evidence 是当前 producer 的完整形状：object 恰含 httpStatus，值为可无损转 int 的 JSON 整数 200；原事件及 operation 的原因与该 producer 一致。缺失、字符串、布尔值、溢出、额外不明字段均不补造。
- 原完整命令及 hash：无 query 的固定 numeric project PUT，原 IID、源 SHA A、合并说明和 `should_remove_source_branch:false`；正文恰三个字段、封套恰五个字段。不能从当前配置重造原请求；不得夹带 auto_merge 或 merge_when_pipeline_succeeds。
- 原 remoteKey、项目身份、session/operation/generation 与 guard 全部匹配；本片 guard.reason 必须为 REMOTE_RESULT_UNKNOWN，其他原因不猜测接纳，不能套用 B14 的 reason=null 假设。

`completeMergeRequest`、原 sender/attempt/hash 与身份校验须复用或原位收敛。metadata 中的“已核对”标志不能替代账本证明。

两种Task历史分别核准：UNKNOWN须有唯一自身UNKNOWN Step及完整归属；RUNNING须保留完整原claim、按数据库时钟已到期、finishedAt为空、无自身Step、原实际执行顺序及同仓前序资格成立。不能把后者的“合格”解释成跳过既有到期/领取证明。

### 3.2 已核查的 GitLab 语义与限度

本轮只读核查正在运行的 `gitlab/gitlab-ce:17.11.3-ce.0`，容器 VERSION 为 17.11.3。该版本 MERGE API 在本次请求未传 auto 参数时同步调用 MergeService，返回后才输出 200；原请求的显式 remove=false 覆盖既有删源设置。MR 之前启用过 auto merge，不会把这个无 auto 参数的请求改成异步排程。

据此选择的是“**本次请求的同步 target 写入已经返回**”子集。它不证明所有 hooks、CI、人工操作或其他既存自动任务以后都不会写仓库。也不把 HTTP 200 当作业务完成证明；业务证明仍见下一节。

源码审查不能替代真实行为验收。B15 首个门禁必须在项目支持的 GitLab fixture 上验证同步响应、关闭 auto 参数和不删源语义；无法确认该协议时保持未决，不用新版文档或状态名称替代证明。版本升级需重验相应契约，不新增通用版本平台。

对 I/O 未确认子集，MR merged 状态机、GitLab 租约和数据库租约均不足以排除已通过检查后暂停的旧请求。该子集不进入 B15。

## 4. 只读观察必须证明原业务计划

B10 的 FOUND 仅证明原 I/IID/A 对象在观察时 merged，未绑定原计划分支，也没有 target 包含 A 的证据，**不能原样消费**。

强观察须在原 seq2 之后取得，绑定原 operation/attempt、原计划/Item/Task、numeric project I、IID、冻结源 A、source/target，并保留实际 HTTP 状态和 JSON 类型：

1. 原 IID 的 GET 为实际 200，完整身份和原分支匹配，MR 为 merged，source SHA 为 A。
2. 只读证明原 target 当前包含 A。复用正常 `GitMergeWorkflow.tryMergeRequest` 的 ALREADY_MERGED 判据及现有 contains 读取；分页、权限或网络不完整不能当 false 或 true。
3. 若 MR 的 merge SHA 非 null，还按正常 `alreadyMerged` 规则确认其包含关系；不存在时清为 null，读取不确定则不收口。
4. 显式 `merge_commit_sha:null` 保持 null；缺失或错类型不补造，不用 A、squash SHA 或当前分支 tip 代填。

强观察的输入、每项结果、取得时间和来源版本必须可审计。网络读取全部在数据库事务外；本地提交前重新核准原计划归属和版本，不在持锁事务里调用 GitLab。

业务含义为“经后续核对，该原计划在观察时已满足”，结果为 ALREADY_MERGED。原 PUT 是否造成合并仍未知，不能改成 APPLIED/MERGED。正常流程与恢复共用成功判断，不建立较宽松的旁路。

target 之后可能被外部重置，这不能通过一次查询永远禁止；当前观察已发现不包含 A 时必须拒绝。Squash 导致无法证明 A 被包含的情况继续未决，不在本片引入另一套内容等价判定。

## 5. 保留历史，追加明确的解决事实

原 operation、seq0/1/2、原 UNKNOWN Step 的内容与时间、原计划、MR 检查点身份、原领取 token 和实际执行顺序保持。不得把历史 UNKNOWN 改写成当时成功，也不得统一忽略所有 UNKNOWN。

优先复用现有 operation event，追加动作限定的 `TASK_RESULT_RESOLVED` 决策事件。该事件是本地业务决定，不伪装为新的 HTTP 响应或 attempt。它至少关联：

- operation、attempt、Run、Item、Task 和原计划指纹；
- 原唯一 Step 的 task_id 与规范内容摘要；RUNNING 无 Step 时明确记录“原结果尚未提交”，不发明历史 Step ID；
- 所消费的 observation ID、原来源版本与提交时当前版本、原 I/IID/A 与原分支；
- 解决结果 ALREADY_MERGED、核对者、恢复者、决定时间、幂等 key 作用域及请求指纹。

实现应让既有事实确实可被下列消费者读取；若需要领域值对象或窄读模型，应围绕这些真实消费者增加，不预建通用恢复实体、任务平台、第二账本或事件总线。

| 现有责任 | B15 必须贯通的变化 |
|---|---|
| GitOperationAppService / EvidencePort / GitRemoteEvidenceAdapter | 生成与计划绑定的强只读观察；原 reconcile 仍只记录观察 |
| GitOperationJournalPort / PersistenceAdapter | 严格原响应证明、观察版本与归属、一次解决事实、guard 确认 |
| RunAppService / RunPort / RunJpaPersistenceAdapter | 原 Run→Task→remote/guard 锁序内核准并提交；不新建 Run/Task 或第二执行器 |
| RunJpaPersistenceAdapter.toDomainItem | 保留 Step 的真实 task_id 关联；当前转换会丢失该关联，不能按动作名猜测被解决的 Step |
| Run.aggregateStatus / RunItem | 仅精确解决的 UNKNOWN 使用有效结果；其他 UNKNOWN、失败和无效证明仍阻断 |
| RunPlanAppService.admitRetry / copyRetryCheckpoint；RunJpaPersistenceAdapter.inheritedPrefix | 成功 Item 不重试；SKIPPED 后继按现门禁进入新 Run；不广泛放宽 UNKNOWN 前缀 |
| RunController / RunJsonView / ReleaseWindowReportView / ExportAppService | 原结果、有效结果与解决来源一致可查；CSV/报告不能继续把已解决项无说明地当未决 |
| MRFirstTimeline / RunDetail / RunDrawer / OrchestrationPanel / runApi | 展示后端有效结果及原 UNKNOWN 来源；时间线与待处理提示共用服务端事实 |

已有 UNKNOWN Task 的当前状态可以由解决事实投影为 COMPLETED，Item 有效结果为 ALREADY_MERGED；原 Step 保持 UNKNOWN，另呈现解决时间与来源。RUNNING 无 Step 则提交首个真实本地结果及同一来源关联，不补造 UNKNOWN Step。

## 6. 恢复入口、幂等与事务

沿现有 RunAppService 增加窄入口 `POST /api/v1/runs/{runId}/resume`，使用必填 Idempotency-Key；请求只选择单个原 `itemId`、`operationId`、`observationId` 和 `expectedOperationVersion`。Task 从服务端归属推导，不接受任意 taskIds、客户端 operator、force 或新计划。

身份与权限沿用现有SecurityConfig的全局认证及SecurityUtils.getCurrentUsername()。原Run.operator/operation.operator核历史归属，当前恢复actor单独记录；两者不强制相同，也不把客户端actor当授权。本片不宣称已有资源级RBAC，不扩建权限平台。

同步确认成功返回 HTTP 200，响应沿用 `ApiResponse<String>` 和原 Run ID；成功表示这一个结果已核准，不表示整个仓库已重启或整个 Run 已 DONE。查询展示解决回执及当前汇总。重复同主体、同 key、同请求先返回已保存回执；同 key 异内容拒绝。对同一 operation 已存在的相同解决事实返回既有结果，不新增事件；冲突证据拒绝。

只读 reconcile 与 resume 分开：前者不会自动触发恢复。前端仅依据服务端给出的资格和阻断原因显示“确认已合并结果”，不由 FOUND 自行推断。

提交事务必须：

1. 先核对原幂等回执，再按 Run→Task→remote/guard 锁序锁定。
2. 重新验证原同步 200、完整请求/发送证明、计划归属、Task/Step 状态和强观察。
3. 消费该次明确版本的最新强观察：请求expectedOperationVersion、当前operation版本与所选观察持久提交后的版本关联必须一致，观察必须在seq2之后。任何后续观察或operation变化都使旧观察失效，须重新核对；不能从旧列表挑FOUND或拼接不同版本的有利字段。不假定版本只增加1，提交后版本或等价持久关联须由实现验证。已消费请求的幂等重放先返回原回执。
4. 同事务追加解决事实、投影 Task/Item/metadata、更新必要绑定与 Run 汇总、确认 guard。任何一步失败全部回滚，原响应和原观察仍保留。
5. 结果无新 HTTP、operation、attempt、claimToken 或租期。RUNNING 原 sender 与两个恢复者竞争最多一个有效提交，失败方不能覆盖赢家。

操作状态仍表达原 UNKNOWN 响应事实；恢复决策与当前 blocked/effectiveResult 由显式解决事实解释。不能仅把 operation.state 改为 APPLIED 来绕过 acknowledgeTask。

恢复原执行事实不重新解释当前窗口范围：冻结或 Detach 不自动禁止只读核对和无 Git 写的事实收口；后续新的 retry 仍遵守当前窗口、绑定、计划与身份门禁。不能将这个例外扩成新 Git 写入权。

## 7. 同仓后继、跨仓与汇总

- 已 UNKNOWN 的路径：原 SKIPPED Task/Item 保持，不能回 PENDING 或伪造执行。源 Run 无剩余未决且确已结束后，才可通过既有 retry 继续符合依赖条件的跳过项。
- RUNNING/无 Step 的路径：只完成原 TRY；原 PENDING 后继继续由现 worker 按原顺序消费，不另建队列。
- 另一个 Item/仓库仍 UNKNOWN 时，Run 继续 NEEDS_REVIEW，finishedAt 仍为空；释放一个匹配 guard 不授予全 Run 重试权。
- 所有原合并项有效完成才为 DONE；还有跳过、失败或活动项时按原仓库汇总与活动任务规则显示，不因一次恢复把整窗涂绿。
- FAIL_FAST、显式 CONTINUE、同仓串行和跨仓推进保持；未解决 UNKNOWN 在两种策略下都停止该仓。

## 8. 失败路径与反例

| 反例 | 必须观察到的结果 |
|---|---|
| 原响应 {} 或非 200，之后出现 merged/contains=true | 本片不受理，原保护和历史保持，写请求计数不变 |
| 只有 later observation 声称 200，原 seq2 无证明 | 不补造同步回执，不收口 |
| 原请求带 auto、remove=true、query 或 hash 不符 | 不收口，不继续同仓写入 |
| I/IID/A 或 source/target 与原计划不同 | MISMATCH/明确阻断；不重定位到当前同名对象 |
| MR merged，但 target 不包含 A | 与正常流程同样不认 ALREADY_MERGED |
| 分页不完整、403、404、网络失败或错误类型 | 保留不完整/不可达语义，不把未知变成功或可重试失败 |
| 新观察已变为 MISMATCH/INCOMPLETE，旧 FOUND 尚在 | 旧观察不再授予恢复权 |
| 另一 UNKNOWN Step 未被关联、伪造跨 Task 解决引用 | Run 仍 NEEDS_REVIEW；不得一并忽略 |
| 两个恢复者、原 sender 与恢复者竞争 | 一个有效事实；无重复 Step/决策/新 Git 写入 |
| 决策、Task CAS、metadata 或 guard 确认中途失败 | 本次修改整体回滚，原响应/观察/保护保持 |
| 原 UNKNOWN 的同仓后继已有 SKIPPED | 不原地重启；只能经既有 retry 重新受理合格后继 |
| 同步200确认后，别的仓库仍运行/未知 | 按真实各仓汇总，不误完成窗口 |
| 当前窗口冻结或原迭代已 Detach | 可核对并修正原事实；新的 retry 继续受现受理门禁限制 |
| merge SHA 为 null 或 squash 无法证明 A 包含 | 不补 SHA；未证明业务条件时继续未决 |

## 9. 实施 DAG 与放行

本片是同一个用户结果的纵向交付，不把每个字段校验拆成新的产品切片：

1. **协议证据门禁**：冻结实现基线；在授权的隔离 GitLab 17.11.3 fixture 验证同步200/非auto/remove=false边界，保留版本与源码证据。源码审查本轮已做，行为验证未做。
2. **独立 RED**：从第1、8节定义原UNKNOWN用户路径、无Step中断、负例和竞争/回滚预期；不能以现函数输出作 oracle。正反例先于生产改动。
3. **强观察与历史模型**：复用只读职责及原事件账本，分别实现计划/包含证明、精确解决关联和领域有效结果；二者可并行，汇合后才能提交恢复。
4. **原事务与真实消费者**：接入窄 Run 恢复、原结果事务、API/查询/导出及现页面，不创建旁路写执行器；删除被替代的重复判断。
5. **端到端验收**：真实 Spring/HTTP/PostgreSQL、原响应及结果事务前后独立 JVM 强杀、双恢复者/旧 sender、现 Run 查询和 retry 出口；页面旅程由 UI 操作创建/变更业务数据，API/DB 仅复核。
6. **交付**：相关回归、静态 TopN 及处理、类型/组件/API契约、OpenSpec严格校验、源码绑定、独立审阅；分组提交推送，并为实现SHA取得相应CI证据。

独立验收按11组责任归档：响应分类、命令/身份、强观察、已UNKNOWN历史、无Step中断、版本竞争、事务回滚、队列/汇总、retry受理、API/UI/导出、固定版本/真实进程。覆盖以风险和行为为单位，不追求字段笛卡尔组合或把重叠测试数相加。

## 10. 风险、回滚及剩余蓝图

主要维护风险是原结果与有效结果出现两套口径，故领域、存储、API、导出和页面必须同片迁移。主要并发风险是旧观察和 guard 的归属变化，故证明消费与结果/保护必须同事务。主要性能风险是额外包含查询，故仅对用户明确核对的精确操作有界读取，不向默认扫描加入无界 HTTP。

若不能满足证明或验收，保留原未决状态；不得降级为 FORCE、普通重试或直接删 guard。已写解决事实后回退，先停新恢复受理，保留支持新审计事实的读取；不得启动会忽略解决事实的旧写服务。不清理历史、操作事件或用户数据。

后续仍分别追踪：真正 I/O 未确认与无seq2 SEND、其他 NEEDS_REVIEW 来源、CREATE_MR/CREATE_BRANCH 的确定拒绝缺口、其他动作恢复、完整跨场景 UI/GitLab 故障旅程及 S4/S5。B15不能被描述为任意 UNKNOWN 自动恢复。SA-002原队列次序不变。

## 11. 本轮证据与来源

项目源码以本文件首部固定基线为准；源码只读证据和717个已跟踪后端/前端文件清单位于 `.ai/reports/validation/c2-b15-contract/source-audit/`。未执行真实 GitLab 写入、没有修改容器或服务。GitLab公开源码从运行容器读取，VERSION和文件SHA256已保存，不能冒称真实行为测试通过。

关键依据：

- `GitWriteHttpInterceptor.java:204–213,242–250,282–289`：完整响应与 I/O 未确认的不同持久证据。
- `GitLabMergeRequestAdapter.java:187–206`、`GitOperationPersistenceAdapter.completeMergeRequest`：原请求、精确项目/IID/A及hash。
- `GitMergeWorkflow.java:146–201`、`GitRemoteEvidenceAdapter.java:110–130`：正常成功判据与B10观察缺口。
- `Run.java:84–105`、`RunJpaPersistenceAdapter.toDomainItem/inheritedPrefix`、`RunPlanAppService.admitRetry/copyRetryCheckpoint`：有效结果、Step真实关联与重试门禁。
- GitLab17.11.3 `lib/api/merge_requests.rb:123–135,749–790`、`app/services/merge_requests/merge_service.rb:70–128`：同步调用及不删源语义；完整文件hash见source-audit/manifest.json。
- [GitLab MR API](https://docs.gitlab.com/api/merge_requests/#merge-a-merge-request)、[合并方法](https://docs.gitlab.com/user/project/merge_requests/methods/#fast-forward-merge)、[MR被其他操作标记merged](https://docs.gitlab.com/user/project/merge_requests/merge_request_troubleshooting/#merge-request-unexpectedly-marked-as-merged)：通用背景；不替代固定版本证据。
