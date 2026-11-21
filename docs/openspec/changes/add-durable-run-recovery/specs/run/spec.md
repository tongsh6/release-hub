# Run 执行规范增量

## ADDED Requirements

### Requirement: 唯一手动编排与权威计划

系统 SHALL 从窗口绑定关系生成仓库并集和 `(window, iteration, repository)` 计划，完整校验后，在同一受理事务保存协议 3 的 Run、不可变快照与每个 Item 的固定四动作任务，再执行 GitLab MR-first。计划包含真实 feature 分支模式、source/target、ref、提供方、仓库映射和稳定顺序；执行及重试不得重新解释这些目标。Publish/Attach 不因此获得隐式 Git 写入权限。

交付与验收事实见 [tasks.md](../../tasks.md) 和项目台账，任务选择以执行路线图的唯一 HEAD 为准。cleanup 未迁入本四动作协议。

#### Scenario: 相同仓库属于多个迭代

- **GIVEN** 同一仓库被窗口中的两个迭代引用
- **WHEN** 用户选择这两个迭代执行 Orchestrate
- **THEN** 二者按 attachAt 与稳定消歧顺序依次合并到同一 release 分支
- **AND** 计划次序与实际执行次序分别记录，共享仓库计数为一

#### Scenario: 所选范围存在无效绑定

- **WHEN** 仓库、迭代、分支模式或远端映射不完整或相互冲突
- **THEN** 整次受理被拒绝且没有 Git 写入
- **AND** 不以过滤无效项后的子集代替用户范围

### Requirement: MR 检查点分段提交

系统 SHALL 以 ENSURE_MR 和 TRY_MERGE 两个独立持久动作执行窗口合并；ENSURE_MR 的 APPLIED 只证明创建、复用或包含关系目标成立，不能代替 TRY_MERGE 的合并确认。TRY_MERGE SHALL 消费已提交的项目、source/target、冻结 source SHA、disposition 及可用的 MR IID/URL 检查点，不得重新创建 MR。cleanup 的实际复合调用 SHALL 复用同一 ensure/try 规则，不形成第二套窗口执行路径。

#### Scenario: 创建或复用 MR 后继续合并

- **GIVEN** ENSURE_MR 已确认唯一 MR，且项目、分支、IID、URL 与冻结 source SHA 完整
- **WHEN** 该动作的结果提交成功
- **THEN** 后继 TRY_MERGE 读取持久检查点并复核同一项目、MR 与 source SHA
- **AND** 只在当前门禁允许时尝试合并，不重新创建或自动关闭 MR

#### Scenario: source 已被 target 包含

- **GIVEN** 精确 source SHA 已被当前 target 包含，且没有可关联的历史 MR
- **WHEN** 执行 ENSURE_MR 与后继 TRY_MERGE
- **THEN** 记录有项目、分支、source SHA 和 ALREADY_MERGED disposition 的证据，IID/URL 可以为空
- **AND** TRY_MERGE 重新确认包含关系，不制造空 MR

#### Scenario: 历史合并 SHA 已不在 target 中

- **GIVEN** 当前 target 仍包含 source SHA，但历史 MR 的 merge SHA 已不被 target 包含
- **WHEN** 确认 ALREADY_MERGED
- **THEN** 不向 cleanup 提供该历史 merge SHA 作为 Tag 依据
- **AND** 不能猜测当前 target tip；包含关系读取不完整时保持 UNKNOWN

### Requirement: 阻塞传播与结果聚合

系统 SHALL 默认 FAIL_FAST，仅显式选择时使用 CONTINUE_ON_BLOCK。FAIL_FAST 下失败或阻塞停止同仓后续 Item 并记录 SKIPPED_DUE_TO_BLOCK；CONTINUE_ON_BLOCK 只允许其他 Item 按原队列继续，不执行当前失败 Item 的后继动作。UNKNOWN 不受继续策略豁免，必须停止本仓后继并使 Run 进入 NEEDS_REVIEW；其他仓库仍可领取并完成其合法任务。

#### Scenario: 第一个迭代阻塞

- **GIVEN** 同仓第二个迭代尚未执行且使用 FAIL_FAST
- **WHEN** 第一个迭代得到 MERGE_BLOCKED 或 FAILED
- **THEN** 第二个迭代记录跳过原因、前置 Item ID 和 executedOrder=0
- **AND** 不丢弃该项，也不把跳过记为成功

#### Scenario: UNKNOWN 只停止发生未决写的仓库

- **GIVEN** A、B 两仓同属一个 Run，且用户显式选择 CONTINUE_ON_BLOCK
- **WHEN** A 仓动作得到 UNKNOWN
- **THEN** A 仓后继任务停止，Run 保持 NEEDS_REVIEW
- **AND** B 仓仍可领取任务、提交结果并完成自己的 guard 确认
- **AND** 不因 Run 为 NEEDS_REVIEW 把 B 仓合法结果拒绝或丢弃

#### Scenario: 所有仓库或部分仓库失败

- **WHEN** Run 完成可确认的执行
- **THEN** 全仓完成为 DONE，全部仓库失败或没有有效执行为 FAILED，部分仓库失败为 PARTIAL_FAILED
- **AND** 任一未决副作用优先 NEEDS_REVIEW；API、分页筛选、详情和导出读取同一持久状态

### Requirement: 重试使用原计划与精确选择

系统 SHALL 只接受原 Run 的实际 RunItem UUID，为已确认失败、阻塞或符合依赖条件的跳过项创建有来源的新 Run/Item 和四动作任务。成功、运行中、预览、UNKNOWN 或不具备协议 3 任务证据的历史 Run 不得普通重试；收尾项不得被转成 feature → release。成功连续前缀 SHALL 以 INHERITED 表达，保留来源与原 MR/项目证据，不生成新的执行 Step、claim 或外部写入。

#### Scenario: 选择依赖阻塞而跳过的项

- **GIVEN** 原计划为 FAIL_FAST
- **WHEN** 用户只选择 SKIPPED_DUE_TO_BLOCK 而未选择其同仓失败前置项
- **THEN** 拒绝受理并解释依赖
- **AND** 不擅自扩大用户选择范围

#### Scenario: 当前分支配置已改变

- **WHEN** 用户重试一个具有有效原计划的失败项
- **THEN** 使用原快照的 source/target、分支模式、顺序和策略
- **AND** 当前映射或绑定不再满足前置条件时拒绝，不改写原计划

#### Scenario: 已创建 MR 的合并被明确阻塞

- **GIVEN** ENSURE_FEATURE、ENSURE_RELEASE、ENSURE_MR 已提交成功，TRY_MERGE 得到可确认的 MERGE_BLOCKED
- **WHEN** 用户按原 Item UUID 受理新的重试
- **THEN** 新 Run 为前三个动作保存 INHERITED，保留原 project ID、MR IID/URL、source SHA 与来源 Run/Item
- **AND** 只领取待执行的 TRY_MERGE，不重复建分支或 MR，不为继承动作伪造本次 Step

#### Scenario: 同仓其他 Item 已固定项目身份

- **GIVEN** 原 Run 的同一仓库已有一致的数字项目 ID，包括由纯读成功 Item 保存的事实
- **WHEN** 选择一个缺少自身项目字段的失败或跳过 Item 重试
- **THEN** 新 Run 继承该仓库唯一已确认项目 ID
- **AND** 原 Run 中项目 ID 冲突、非法或新的远端映射不一致时拒绝，不重新解析为另一个目标

### Requirement: 确认身份与未决写保持一致

系统 SHALL 从首次确认的 GitLab 分支读取开始在当前任务上下文固定数字项目 ID，而非等到首次 HTTP 写入；动作提交时与结果一起写入 Item metadata，写意图按既有 operation 固定远端目标。已有持久身份 SHALL 约束同 Run、同仓库的后继任务、其他 Item 和重试；不同仓库分别绑定。未提交且无 operation/Step/metadata 的内存读取不是跨进程检查点。所有真实读写 SHALL 校验期望身份并使用 numeric URI；业务 UNKNOWN 不得覆盖已确认的项目或 MR 检查点。传输层某请求 APPLIED 不能代替业务证据完整性。

#### Scenario: 首次分支已存在且没有写 operation

- **GIVEN** ENSURE_FEATURE 只读确认项目 A 的 feature 分支存在，未产生写账本
- **WHEN** 该动作提交 ALREADY_EXISTS
- **THEN** 项目 A 的数字 ID 与动作结果一起持久化
- **AND** 后续 ENSURE_RELEASE 即使是本 Run 首次写入，也必须使用该期望身份
- **AND** namespace 已改向项目 B 时，在发送写请求之前拒绝

#### Scenario: 分支不存在但身份无法确认

- **WHEN** 分支读取缺少项目身份、有效分支证据，或未取得真实分支 404
- **THEN** 不将该响应当作可创建的 missing
- **AND** 不发送建分支请求；真实分支 404 仍须携带已确认项目 ID

#### Scenario: 已确认写入后的业务证据不足

- **GIVEN** 已有 HTTP APPLIED 事实，但后续业务读取不可用或结果证据不足
- **WHEN** ENSURE_MR 或 TRY_MERGE 返回 UNKNOWN
- **THEN** 结果事务保留 NEEDS_REVIEW 与仓库 guard
- **AND** 任务 scope 正常退出不能提前释放 guard，后续新 Run 的写请求在远端发送前被拒绝
- **AND** CREATE_MR 原始响应不满足完整证明时直接记录 UNKNOWN，不因 HTTP 2xx 记为 APPLIED；此时不满足上述 APPLIED 前提

#### Scenario: TRY 返回不完整或不一致的身份

- **GIVEN** 已提交 checkpoint 的项目、MR IID 与 source SHA 均已确认
- **WHEN** TRY 返回 UNKNOWN，或“成功”响应对应另一个项目、MR 或 source SHA
- **THEN** 记录 UNKNOWN 及阻塞证据，保留原 checkpoint 身份
- **AND** 不用 null、部分响应或错误项目覆盖原事实

#### Scenario: namespace 在身份解析后被重用

- **GIVEN** namespace 读取对应项目 A，随后该路径可能映射项目 B
- **WHEN** 派发已通过期望身份校验的 Git 写请求
- **THEN** 最终 URI 固定为项目 A 的数字 ID，保留原动作 suffix 与 raw query
- **AND** 不向可变 namespace 发送写请求，不向远端发送内部身份或任务控制头

### Requirement: 外部写入前持久受理

系统 SHALL 在协议 3 窗口编排的每次外部写入之前，先持久化 Run、封存计划、固定四动作任务与操作意图；协调器不得用覆盖整轮网络调用的数据库事务代替检查点。C2-A 请求线程只受理并返回持久回执，后台消费者只执行已领取任务；重复受理不得从内存计划或旧直接循环继续执行。

#### Scenario: 受理提交后进程退出

- **GIVEN** 协议 3 Run 已受理但没有派发外部写入
- **WHEN** 进程重启并查询或以相同 key 和请求重新提交
- **THEN** 原 Run 与任务仍可查询，相同请求返回原 Run
- **AND** C2-A 的持久扫描只领取原计划中仍为 PENDING 且满足同仓前序约束的任务，不因查询或重复请求新增执行意图

#### Scenario: 已提交 MR 后独立进程被强制终止

- **GIVEN** 独立进程已完成并提交 ENSURE_MR，TRY_MERGE 尚未领取
- **WHEN** 该进程被强制终止，新进程使用同一数据库与相同请求 key 重放受理
- **THEN** 返回原 Run，ENSURE_MR 保持 COMPLETED，MR 身份与 source SHA 保留
- **AND** C2-A 后台扫描仅领取原 PENDING TRY_MERGE，以原项目/IID/SHA 完成合并及原子投影，MR POST 不增加、MERGE PUT 恰一次
- **AND** 此场景不作为 SEND_STARTED 后结果未提交、已领取任务重领或 lease 接管的恢复证明

#### Scenario: 计划预览不执行 Git 写入

- **WHEN** 用户创建 S1 持久计划预览
- **THEN** 系统返回可查询的 PLANNED 计划，不创建协议 3 执行任务
- **AND** 不派发任何 Git 写请求

### Requirement: 任务结果与仓库保留原子提交

系统 SHALL 对协议 3 任务按同一 claim 身份定向提交唯一 Step、Item 结果与 metadata、必要绑定、任务终态及 Run 汇总，并在同一短事务内确认 guard；锁定顺序为 Run → Task → guard。任务保持 RUNNING 时校验 taskId/claimToken，确认成功后才进入终态。系统 SHALL 拒绝用旧完整 Run 聚合保存覆盖已有持久任务的结果。

#### Scenario: MR 检查点提交失败

- **GIVEN** MR 外部操作已经发生，但 ENSURE_MR 结果事务无法提交
- **WHEN** 协调器收到数据库或业务投影提交异常
- **THEN** 异常向调用方传播，Step、Item、任务终态与 guard 确认一并回滚
- **AND** 独立操作账本保留已发生的 HTTP 事实，guard 不被 scope 关闭释放
- **AND** 不领取或派发 TRY_MERGE，不把提交异常转换为可继续的普通失败

#### Scenario: 两个仓库的结果先后到达

- **GIVEN** A、B 仓的任务均已合法领取
- **WHEN** 两个执行结果分别提交
- **THEN** 各自定向更新任务与 Item，并根据数据库事实重新汇总 Run
- **AND** 后到的旧内存 Run 不能覆盖另一仓已提交的 Step、metadata、状态或绑定

#### Scenario: 同一任务重复提交结果

- **GIVEN** 某 taskId/claimToken 的结果已经提交终态
- **WHEN** 同一或不匹配领取身份再次尝试提交
- **THEN** CAS 拒绝重复结果，不产生第二条 task Step
- **AND** 不重复释放 guard 或改变已保存的执行事实

### Requirement: 运行身份与请求幂等隔离

系统 SHALL 为新 Run、RunItem 与持久 task 分配独立、不碰撞的标识；Orchestrate 和协议 3 Item retry SHALL 要求非空 Idempotency-Key，使用持久 actor、原始 key 与包含动作语义的规范化请求 hash 去重。请求 key 不是 taskId、claimToken 或可选服务端随机值；后续运行不得改变历史执行项归属。C1 不引入独立 Attempt 实体或租约代次。

#### Scenario: 同范围多次执行

- **WHEN** 同一仓库、迭代、窗口以不同合法意图被纳入两个新 Run
- **THEN** 两个 Run 的 Item 与 task 独立保存
- **AND** 第一次运行的执行项和证据保持不变

#### Scenario: 缺少必填请求 key

- **WHEN** Orchestrate 或 retry 请求缺少 Idempotency-Key，或 key 为空白
- **THEN** 明确拒绝请求，不创建 Run、计划或 task
- **AND** 不用随机 key 代替调用方意图，不派发 Git

#### Scenario: 同幂等键重复请求

- **WHEN** 同一调用主体以相同 key 和相同规范化请求再次提交
- **THEN** 返回原 Run，包括仍在运行或安全停住的 Run
- **AND** 不重新校验为另一个动态范围，不领取或重复派发任务

#### Scenario: 幂等键对应不同请求

- **WHEN** 同一主体已使用的 key 对应不同动作、范围、策略或 retry 选择
- **THEN** 返回冲突
- **AND** 不修改原计划，不以添加 key 前缀或另建执行入口规避冲突

### Requirement: 未知结果先核对

系统 SHALL 保留可能已到达外部系统但未确认结果的写请求及仓库 guard，不得当作普通失败直接重试。C1 在调用线程取得未决结果后提交 UNKNOWN；若进程在结果提交前终止，则保留数据库中真实的 RUNNING/SENT 等事实，不伪造任务完成或自动恢复。独立只读观察不能自行授权继续；核对后原子补记与自动继续属于 C2 目标。

#### Scenario: Git 成功但本地结果尚未提交

- **GIVEN** SEND_STARTED 已持久化，外部操作可能成功但业务结果未提交
- **WHEN** 当前请求失败或进程中断
- **THEN** 保留已有操作证据与仓库 guard，不因时间经过或重复受理重新发送
- **AND** C1 查询返回实际已保存状态，不宣称自动恢复已完成

#### Scenario: 核对已发生的外部成功（C2 目标）

- **GIVEN** 后续恢复执行器已通过精确操作身份和计划证据确认外部成功
- **WHEN** 执行证据驱动的恢复提交
- **THEN** 只补记尚缺的本地结果，不重放已发生的外部写入
- **AND** 只有结果、任务与 guard 原子确认后才允许后继继续

#### Scenario: 查询未找到结果但原请求可能在途

- **WHEN** 核对没有找到目标，但不能证明原请求已终结且未生效
- **THEN** 保持未决记录或进入 NEEDS_REVIEW
- **AND** 不以一次不存在响应为依据再次发送写请求

### Requirement: 仓库写入互斥与过期执行者隔离

系统 SHALL 对同一规范化仓库建立原子写入保留，覆盖当前真实内部入口。C1 SHALL 在意图记账、发送及结果确认边界校验持久 taskId/claimToken、Run、actor、repo 和动作资格；不匹配身份不得发送或提交。C1 不存在超时重领或 lease 接管；C2 的租约过期也只允许接管核对，不能自动释放未决副作用的保留。

#### Scenario: 领取身份不匹配或任务已经结束

- **GIVEN** taskId 的持久 claimToken 与调用方不符，或任务已不处于允许执行的状态
- **WHEN** 调用方尝试登记意图、发送请求或提交结果
- **THEN** 在相应持久边界拒绝该动作
- **AND** 不产生新的远端写入，不覆盖现有结果或释放 guard

#### Scenario: 旧 worker 返回（C2 目标）

- **GIVEN** 后续 worker 机制已更换任务领取身份
- **WHEN** 旧 worker 尝试保存结果或继续派发
- **THEN** 数据库 CAS 拒绝旧结果覆盖
- **AND** 旧 worker 不得继续派发后续写请求

#### Scenario: 未决仓库被其他入口操作

- **GIVEN** 仓库仍有 SENT、UNKNOWN 或待本地结果确认的写操作
- **WHEN** 另一个 Run 或其他真实业务入口请求写该仓库
- **THEN** 返回明确阻断，不绕过保留

#### Scenario: 冲突检查没有已有 MR

- **GIVEN** 合并检查未找到可唯一确认的已有 MR
- **WHEN** 用户请求冲突检查
- **THEN** 返回无法确认的读取结果
- **AND** 仅允许 GET/HEAD，不创建或关闭临时 MR，不产生写账本

#### Scenario: 仓库别名与配置改向

- **GIVEN** 两个本地仓库标识对应同一远端项目，且该项目存在未知操作
- **WHEN** 另一个入口请求写入或尝试修改原仓库的远端映射
- **THEN** 使用同一远端保留阻断操作，不允许改向后绕过

### Requirement: 操作证据独立持久化

系统 SHALL 为受控的新旧写入口维护独立操作账本，即使来源尚无持久 RunTask，也必须先提交意图与发送事件；业务事务回滚不得删除已发生外部操作的证据。

#### Scenario: 外部成功后业务事务回滚

- **WHEN** 外部操作已发送且成功，但外层业务事务回滚
- **THEN** 操作意图和已确认的外部证据仍可查询
- **AND** 本地业务结果未确认时保留待复核阻断，不自动重放

#### Scenario: 本地文件模式无法识别受保护目标

- **WHEN** 受控入口请求写本地文件，但无法确定目标身份及保留关系
- **THEN** 在修改文件前明确拒绝
- **AND** 不回退到无门禁的文件访问实现

### Requirement: 只读核对保留不确定性

系统 SHALL 为只读核对使用独立的外部证据查询接口，区分权限失败、不可达、不完整、不支持和确认观察结果；核对不得发出外部写入，过时观察不得覆盖新的状态。

#### Scenario: 核对查询不完整

- **WHEN** 核对遇到权限失败、分页未完成或网络异常
- **THEN** 保留未知或人工复核状态
- **AND** 不把空结果当成可以安全重试的证据

#### Scenario: 幂等核对与外部零写入

- **WHEN** 同一主体用相同幂等键和请求内容重复核对
- **THEN** 返回同一核对记录
- **AND** 外部请求仅允许 GET/HEAD，不创建或关闭 MR，不修改引用或文件

### Requirement: 归档删除具有确认依据

系统 SHALL 将归档引用创建和源引用删除作为独立操作；只有归档已确认且源引用满足经验证的原子删除前置条件时，后续 S4 才可实现受控删除。当前 C1 真实 HTTP DELETE SHALL 继续拒绝；证据不足或提供方并发保护能力未证实不能授权发送。

#### Scenario: 当前入口请求真实分支删除

- **WHEN** 当前实现收到真实 GitLab HTTP DELETE 写请求
- **THEN** 在远端发送前明确阻断
- **AND** 不以完成恢复闭环为由绕过保护

#### Scenario: 归档创建失败（S4 目标）

- **WHEN** 归档引用创建失败或响应无法确认
- **THEN** 不发送源分支删除请求
- **AND** 保留失败或未知的独立证据

#### Scenario: 源引用在归档后漂移（S4 目标）

- **WHEN** 删除前发现源引用已变化，或不能可靠保障删除前置条件
- **THEN** 阻断删除并要求复核
- **AND** 不把旧观察当作新的删除授权

### Requirement: 历史记录与补偿边界

系统 SHALL 保留旧 Run 的查询导出，不自动构造历史 task、claim、Step 或操作意图，不重放旧执行路径；无协议 3 证据的旧 Run 不能通过新 retry 被隐式升级。补偿属于后续有来源、前置条件和明确授权的新计划。

#### Scenario: 打开历史 Run

- **WHEN** 用户查看无新协议元数据的历史 Run
- **THEN** 按实际已保存事实及单向规范化的统一执行状态展示记录
- **AND** 不补造任务或提供无证据的自动恢复

#### Scenario: 合并已经发生

- **WHEN** 后续步骤失败
- **THEN** 不自动回滚合并、删除已有引用或重触发 CI
- **AND** 保留已发生事实和人工决策入口

### Requirement: 无写意图过期任务的受控接管

系统 SHALL 在 C2-B1 沿同一执行器接管当前 WINDOW 协议中无任何 operation 和结果 Step 的过期 RUNNING 任务。候选发现和领取 SHALL 同时支持只有最后一个 RUNNING、没有 PENDING 的 Run；扫描只提供候选，资格由 Run→Task 锁内复核及旧 token 条件更新决定。到期本身不得撤销已有发送/结果归属，也不得释放 guard。

#### Scenario: 最后一个任务在写意图前中断

- **GIVEN** 原 Run 前三动作已完成，TRY_MERGE 已领取且没有 PENDING、operation 或该 task 的 Step
- **WHEN** 数据库期限已到，后台扫描并领取
- **THEN** 原任务更换 token 和期限后由唯一执行器继续，Run/Item/task ID、冻结项目/IID/SHA 和实际顺序不变
- **AND** 独立进程强杀重启后 MR POST 不增加、MERGE PUT 恰一次，成功前缀不被改写

#### Scenario: 两个接管者竞争

- **GIVEN** 两个 worker 同时发现同一过期任务
- **WHEN** 它们竞争 Run→Task 锁
- **THEN** 只有一个取得新 token，另一方不得越过已更新的数据库期限
- **AND** 线程池拒绝或排队取消不更换 token

#### Scenario: 旧写意图先赢得锁

- **GIVEN** 扫描时尚无 operation，旧 worker 随后先提交了 begin
- **WHEN** 新 worker 在锁内复核接管资格
- **THEN** 发现同 task_id 的 operation 后拒绝接管，无论其 token 或状态
- **AND** 不修改原 token、操作事实或 guard；不得仅排除 SEND/UNKNOWN

#### Scenario: 新接管先赢得锁

- **GIVEN** 新 worker 已在锁内确认无 operation/Step 并更换 token
- **WHEN** 旧 worker 的 GET 返回并尝试 begin 或 completeTask
- **THEN** 旧 token 在实际 HTTP 写或结果提交前被拒，旧请求零写、零新增 Step
- **AND** 新 worker 完成原任务后，旧结果不能覆盖其投影

#### Scenario: 结果和时间事实不被误判

- **GIVEN** 某 task 已有任意 operation 或 Step，或原 token/首次 started_at/期限缺失，或实际顺序非正，或期限未到，或 Task 已终结/UNKNOWN
- **WHEN** 扫描或领取
- **THEN** 该 task 不因过期规则获得接管资格，原 token、期限和顺序不变，也不补造缺失事实；查询错误不得降级为“没有证据”
- **AND** 同 Item 其他 task 的成功 Step 不妨碍最后一个无写意图任务接管

#### Scenario: 拿锁后的数据库期限与本次步骤时间

- **GIVEN** 领取事务在 Run 锁上等待，或者新旧 worker 的应用时钟存在偏差
- **WHEN** 真正取得锁并成功领取或接管
- **THEN** 当前期限依据拿锁后的数据库当前时间重新设置，不使用事务开始时刻或首次 Task 时间
- **AND** Task.started_at 保留首次领取；新 TaskClaim/Step 的起止时间来自本次 worker，二者不要求与首次 Task 时间相等
- **AND** 到期只允许竞争接管，不在 begin/sending/finish/ack 中单独撤销仍匹配的 token

### Requirement: 已确认建分支结果的受控投影恢复

C2-B2 已完成本地门禁与固定 cc80036 的 Full-Link 44917254215 5/5 验收并交付；依赖 C2-B1 的固定 SHA 验收已满足。规范化成功证明与 APPLIED 建分支投影恢复在同一切片交付。

系统 SHALL 沿现有任务与账本，只对当前 WINDOW 协议的 ENSURE_FEATURE/ENSURE_RELEASE 补原 Task 投影：Task 为完整到期 RUNNING、实际顺序为正、无当前 Step，唯一 CREATE_BRANCH/APPLIED operation 具有 HTTP 201 及匹配的规范化 name/commit.id 证明。正常创建与事件共用此判定。系统 SHALL 保留原 operation/token/期限，在 Run→Task→guard 同一结果事务提交 Step、Item、绑定、Task 终态、Run 进度并确认 guard；恢复阶段零网络。不新增实体/API/UI/通用平台，不改变 GET 分支或 Tag 契约。

#### Scenario: 建分支结果已确认但投影未提交

- **GIVEN** 原 CREATE_BRANCH 的 HTTP 201 与完整匹配响应证明已提交 APPLIED，进程在 Task/Step 结果事务前被强杀
- **WHEN** 默认 worker 在到期后处理原任务
- **THEN** 仅补该 Task 的业务投影并原子确认 guard，原 Task/Item/operation 身份、token、期限、顺序与成功前缀保持
- **AND** POST 总数仍为 1，恢复阶段 GET/POST/PUT 等远端请求均为 0，单次 worker 只完成一个任务

#### Scenario: 命名 ref 与冻结提交各自约束原事实

- **GIVEN** plan.defaultBranch 与 operation.target.sourceRef 保存原 ref 表达，operation.target.ref 保存写前已解析的提交 SHA
- **WHEN** 判断分支证明是否足以补投影
- **THEN** sourceRef 必须精确等于原计划 ref，target.branch 必须精确等于对应计划分支；target.ref 和规范化响应 commit.id 均须为 40/64 位合法十六进制 SHA，随后以忽略大小写的值比较身份
- **AND** 不把命名 ref 直接当作 SHA，不查询当前 ref；numeric project 取原 operation 并与同 Run/仓库已持久 git.projectId 一致，namespace 改向不能替换该身份

#### Scenario: 旁支字段不能掩盖错误 commit.id

- **GIVEN** CREATE_BRANCH 返回 HTTP 201、正确 name、错误但合法的 commit.id=B，同时 object.id 或 commit.sha 为原 target.ref 的冻结 SHA A
- **WHEN** 正常创建、HTTP 事件证明或恢复投影判断成功
- **THEN** 只认确切 commit.id=B，因其不等于 A 而不确认 CREATED 或可恢复的 APPLIED 成功证明，不补成功 Step、不清 guard
- **AND** commit.sha、object.id/object.sha 等字段不得替代或覆盖 commit.id，包括 commit.id 本身正确时；规范化证明始终保留该确切来源

#### Scenario: HTTP 摘要不冒充业务完整证明

- **GIVEN** HTTP 201 摘要只有 object.id/通用 commitSha，或 httpStatus 不是整数，或 name/commit.id 类型、来源、分支、SHA、项目或 attempt 不匹配
- **WHEN** operation 状态为 APPLIED，或者通用只读观察为 FOUND
- **THEN** 不补成功 Step、不清 guard、不改变 token/期限/顺序，不补造旧摘要的证明
- **AND** MR 的 APPLIED/FOUND、INTENT、SEND_STARTED、UNKNOWN/NEEDS_REVIEW、已有 Step 或领取事实不完整均不获得本片恢复授权

#### Scenario: 非 201 响应不确认创建完成

- **GIVEN** CREATE_BRANCH 返回 202 Accepted 或其他非 201 的 2xx，即便正文 name/commit.id 完整且匹配
- **WHEN** 正常创建处理响应或恢复检查已有摘要
- **THEN** 不把该响应当作 CREATED 或可恢复的 APPLIED 成功证明，保留未确认结果及 guard，不因正文看似成功而放行
- **AND** 此创建约束不改变 GET 分支的成功响应或 Tag 等其他动作的确认规则

#### Scenario: 结果与保护同事务且迟到结果不能覆盖

- **GIVEN** 两个恢复 worker，或原 owner 与恢复 worker，竞争同一 Task
- **WHEN** 结果事务按 Run→Task→guard 加锁并复核完整事实
- **THEN** 最多提交一个 Step；先成功提交者保留投影，后提交者不覆盖，原 owner 先提交 UNKNOWN 时本片不恢复
- **AND** guard 归属不符或投影后故障使业务结果与 guard 一起回滚，不新建 operation、不重发、不派发被阻塞后继

#### Scenario: 结果事务不被自调用或新事务拆开

- **GIVEN** 外层投影处理已按 Run→Task 取得锁
- **WHEN** 复用 completeTask 的提交核心并调用 Journal 证据检查和 ack
- **THEN** 外层入口须经 Spring 代理建立真实事务，同类 self-call 注解不另起事务，Journal 检查以 MANDATORY 加入，不用 REQUIRES_NEW 重取外层锁
- **AND** 锁重入只限同一物理事务，flush/clear 后不修改已脱离上下文的旧实体，任何结果或 ack 失败均向外传播并整体回滚
- **AND** 期限仅筛候选，不因证据不足释放 guard、续租或退回 PENDING，也不单凭到期撤销仍匹配的原 finish/ack

#### Scenario: 补投影处理时间与历史时间分开

- **GIVEN** 当前 worker 与原 worker 的时钟存在偏差，原 Task 首次时间及 HTTP event 时间已持久化
- **WHEN** 当前 worker 成功补投影
- **THEN** 新 Step.startAt/endAt 使用当前 worker 同一 Clock 的处理起止，明确表示本次补记；原 Task.started_at 与 HTTP 历史时间不变
- **AND** 不伪造原 HTTP 执行时段，不用 max() 修饰跨时钟差异；停止派发回滚时保留已提交事实，不撤销分支或退回 PENDING

### Requirement: 已确认创建 MR 结果的受控检查点恢复

C2-B3 已 DONE：d87dfa8、544983b 已推送，固定完整 SHA `544983beff74604852c050209e4c8caa93678ef2` 的 Full-Link 44924378741 为 5/5。本地与远端证据见记录 16.4–16.6，中间与失败历史保留于 16.2–16.3；以下仍仅限定本片 CREATE_MR 的已验收范围。系统 SHALL 复用原 Task、操作账本和结果事务，仅对完整到期的原 ENSURE_MR/CREATE_MR/APPLIED 补历史 CREATED 检查点，随后由原 TRY_MERGE 复核并使用同一项目/IID/source SHA；纯 DB 投影不得重新 GET 或 POST。

#### Scenario: 规范创建证明与原检查点

- **GIVEN** 原 operation 保存计划 source/target 和冻结 source SHA，响应属于同一 attempt 的 sequence 2 APPLIED
- **WHEN** 原始响应为 HTTP 201，三个项目 ID 为正整数并均等于原 numeric project，分支精确一致，sha 与原冻结值一致，iid 为正整数、web_url 合法且 state 为 opened
- **THEN** 正常创建、HTTP 账本和恢复使用相同证明；原 Task/Step/Item 检查点与 guard 在同一结果事务提交 CREATED
- **AND** 保留原 token、期限、首次时间、operation/events 与执行顺序，新 Step 只记当前处理时间；metadata 的 null 清除规则与正常路径共用
- **AND** readiness 为 checking、审批或流水线等待不阻止 CREATED；当前可合并性由 TRY_MERGE 独立判断
- **AND** CREATE/opened 的 mergeSha 为 null，创建证明与事件白名单不采用 merge_commit_sha，不让附带值变成本次合并事实

#### Scenario: 非规范响应或历史证明不足

- **GIVEN** 非 201 的完整正文，或 201 缺少字段、字段类型错误、fork 项目不同、分支或冻结 SHA 不同、不是 opened，或只有旧扁平 APPLIED 摘要
- **WHEN** 正常创建确认响应或恢复验证原证据
- **THEN** 不确认成功、不补检查点、不解除 guard，不从请求意图补造响应字段，全局 id 不替代 iid
- **AND** 原 Task 已有 Step、领取不完整、attempt/guard/计划不一致或 owner 已提交 UNKNOWN 时也不恢复；不续租、不回 PENDING、不重发

#### Scenario: 恢复历史创建不越过当前 MR 状态

- **GIVEN** 原完整创建证明允许补记 source SHA A 的检查点，但远端 MR 已关闭或 source 已变成 B
- **WHEN** 纯 DB 投影提交后原 TRY_MERGE 继续，target 尚未包含 A
- **THEN** 后继按原 IID/SHA 复核并保持 UNKNOWN，不重新创建 MR、不合并 B、不覆盖原检查点
- **AND** 若 target 精确包含 A，仍按原规则返回 ALREADY_MERGED，不因后续关闭或源变化误判失败

#### Scenario: 原检查点 SHA 的等值表示保持一致

- **GIVEN** 正常创建或纯 DB 恢复保留响应 SHA 大写 A，后续原 IID 的 GET/MERGE 返回同一个合法 40/64 位摘要的小写 a
- **WHEN** 原 TRY_MERGE 复核、合并并提交结果
- **THEN** 按摘要值确认身份，不把大小写差异误判为 UNKNOWN；合并请求仍使用原检查点 A，已存检查点和创建事件不改写
- **AND** 真实不同的 SHA B 仍拒绝，不能放宽项目、IID、分支、状态或既有响应解析规则

#### Scenario: 创建结果后的独立进程中断

- **GIVEN** CREATE_MR/APPLIED 与完整规范响应已提交，ENSURE_MR 业务结果尚未提交
- **WHEN** 独立 JVM 被强杀并由默认 worker 重启继续
- **THEN** 既有 PENDING 后继使扫描发现原 Run；投影阶段 HTTP=0，原 MR POST 总数=1，原 Task 身份、claim token、期限及首次时间不变，operation/events 不变，后继沿原检查点推进
- **AND** 双恢复者和原 owner 竞争最多提交一个 Step，迟到结果不能覆盖；结果或 ack 故障整体回滚
- **AND** 本片不授予 MERGE_MR、INTENT/SEND/UNKNOWN 或其他执行类型的恢复权限

### Requirement: 已确认合并 MR 结果的末尾原任务恢复

C2-B4 为当前唯一实施队首，以下为实施前契约；只读定位已完成，源码尚未实施，新测试待 RED。系统 SHALL 复用原 Task、操作账本和结果事务，对完整到期的原 TRY_MERGE/MERGE_MR/APPLIED 补历史 MERGED 结果；即使整个 Run 没有 PENDING 后继也 SHALL 被默认 worker 发现。恢复 SHALL 零 HTTP，不授予 INTENT/SEND/UNKNOWN 或其他执行类型恢复权限。

#### Scenario: 同一原响应的完整合并证明

- **GIVEN** 原请求已冻结 numeric project、MR IID 和合法 source SHA
- **WHEN** 响应为 HTTP 200、文本 state=merged，原 JSON 三个项目 ID 为正整数且等于原项目，iid 为正整数且等于请求，分支为非空白文本、web_url 合法、40/64 位 source SHA 与冻结摘要等值
- **THEN** 正常 merge、HTTP 账本与 Journal 共用 confirmedMergedMr(status, raw, projectId, iid, expectedSha)，保留原 JSON 类型与 SHA 表示，不用意图补响应
- **AND** 不增加 nullable 分支参数或内部 header；精确分支仍由既有 workflow 检查点与 Journal 原 claim 各自绑定

#### Scenario: 合并提交的显式 null 与缺字段不同

- **GIVEN** 完整 merged 响应的 merge_commit_sha 字段存在
- **WHEN** 该字段为 JSON null，或为合法文本 SHA
- **THEN** FF/FF+squash 的 null 与普通合并的合法 SHA 均可成为完整证明
- **AND** 字段缺失、错误类型、空白、字符串 null 均不成立；squash_commit_sha、source SHA 或其他字段不得补造 merge_commit_sha

#### Scenario: provider 证明不替代原业务绑定

- **GIVEN** 非 200 或不完整/错类型响应，或者原项目/IID/source SHA 与请求矛盾
- **WHEN** 正常 merge 或恢复核对规范证明
- **THEN** 不确认完整成功，不清保护，不从请求填空
- **AND** 若 provider 响应完整但 target 与原检查点矛盾，即使 HTTP 事件已 APPLIED，业务也不得成功；正常结果保留 UNKNOWN/NEEDS_REVIEW 与 guard，投影拒绝

#### Scenario: 原检查点身份与可空结果分别更新

- **GIVEN** 原 ENSURE_MR 检查点 disposition 为 CREATED 或 MR_REUSED，原 MERGE_MR 请求绑定该 IID/source SHA；ALREADY_MERGED 没有 PUT 证明
- **WHEN** 正常 TRY 或恢复提交 MERGED，响应 source SHA 与原值为同一个合法 40/64 位摘要的不同大小写表示
- **THEN** 保留原 identity 字节及原 disposition，只使用正常 TRY 的 MR_RESULT 四字段 patch，不套用 ENSURE_MR 全量 identity patch
- **AND** 恢复的 requestId 为 null；null 按原 patch 规则清旧 requestId/blockReason，合法 null mergeSha 清旧 mergeSha，不制造新请求 ID 或新 disposition

#### Scenario: 末尾候选发现与锁内授权分离

- **GIVEN** 当前 WINDOW 协议活动 Run 无 PENDING，原 TRY_MERGE 为完整到期 RUNNING，存在 MERGE_MR/APPLIED 且无当前 Step
- **WHEN** 原有有界扫描发现该 Run
- **THEN** 候选查询不领取或更换 token，不复制 JSON 证明；原投影入口在 Run→Task→guard 锁内重核完整到期、实际顺序、仓内前序、唯一原 operation/同 attempt sequence 2、token/session/generation、项目、suffix/SHA、计划与检查点
- **AND** 坏证据占满前页时 worker 游标仍向后推进并到尾回绕，后页合法末尾候选不会因此永久饥饿

#### Scenario: 不完整资格或已提交 owner 结果拒绝投影

- **GIVEN** 原领取未完整到期、缺必要领取事实、当前 Step 已存在，或 attempt/guard/checkpoint/计划不一致，或 owner 已提交 UNKNOWN
- **WHEN** 恢复尝试核对该 Task
- **THEN** 不补结果、不换 token、不续租、不改 operation/events、不解除保护、不重新发送
- **AND** 不把 INTENT/SEND/UNKNOWN、旧摘要或 ALREADY_MERGED 检查点当作本片成功证明

#### Scenario: 历史 MERGED 与当前远端状态分离

- **GIVEN** 原 MERGE_MR 完整 APPLIED 证明已持久化，而后 target 被重置或远端读取不可用
- **WHEN** 默认 worker 补原任务结果
- **THEN** 零 HTTP 确认原历史 MERGED，保留原 token/lease/首次时间/执行顺序及 operation/events
- **AND** 新 Step 与绑定 lastMergeAt 使用当前处理 Clock，不将补记时间冒称远端实际合并时刻

#### Scenario: 竞争与完整结果事务

- **GIVEN** 双恢复者竞争，或原 owner 正常/UNKNOWN 结果与恢复先后提交
- **WHEN** 原 completeTask 结果事务竞争同一任务
- **THEN** 最多一个 Step；已提交正常或 UNKNOWN owner 结果保持，恢复先提交后迟到 owner 不覆盖
- **AND** 事务回滚时 Task/Step/metadata/Item/Run/绑定/guard 全部回滚，不留下部分完成

#### Scenario: 仓内前序与跨仓终态

- **GIVEN** 同仓前序未满足，或其他仓库/迭代仍在途、失败或 UNKNOWN
- **WHEN** 原任务投影及结果汇总执行
- **THEN** 同仓不越序，跨仓仍可推进；绑定和各仓库结果分别确认，沿原 Run 聚合，不因单个 MERGED 硬写 DONE
- **AND** 在途任务或 NEEDS_REVIEW 仍保持未结束；只有原聚合满足结束条件时才写 finishedAt

#### Scenario: 最后合并写入后的独立进程中断

- **GIVEN** 最后 MERGE_MR/APPLIED 已提交而原 TRY 业务结果未提交，全 Run 没有 PENDING 后继
- **WHEN** 独立 JVM exit=137 并由默认 worker 重启
- **THEN** 原 Run 被扫描发现并完成受控投影，恢复阶段 HTTP=0，原 merge PUT 总数=1，身份/历史保持且最终汇总正确
- **AND** 回滚先停派发并保留事实，不撤销已发生的 merge；cleanup/version、完整 UI 及其他恢复仍需独立验收

## MODIFIED Requirements

### Requirement: 运行任务管理

系统 SHALL 为 executionVersion=3 的 WINDOW_ORCHESTRATION 在受理事务中持久化固定四动作任务，复用现有 run_task 及 Run/Item 身份；这些记录由唯一任务执行器消费，C2-A 起由有界后台 worker 驱动，请求线程仅返回受理回执。任务列表 SHALL 展示实际持久字段，不把未来依赖图、Attempt 或 lease 摘要当作已有 API。

#### Scenario: 创建运行任务

- **GIVEN** 一个通过完整校验的新协议窗口编排
- **WHEN** 封存计划并提交受理
- **THEN** 每个 Item 按固定次序保存 ENSURE_FEATURE、ENSURE_RELEASE、ENSURE_MR、TRY_MERGE
- **AND** 新执行动作初始为 PENDING；retry 已确认成功前缀为 INHERITED
- **AND** Run、快照和任务一并提交，禁止先执行后补记录

#### Scenario: 查看运行任务列表

- **GIVEN** 新协议 Run 已创建任务
- **WHEN** 用户调用 GET /api/v1/runs/{id}/tasks
- **THEN** 按 taskOrder 返回实际 taskId、runId、itemId、actionType、status、startedAt、finishedAt 与脱敏 errorMessage
- **AND** 不返回 claimToken、访问 Token、内部请求头或敏感响应，不伪造尝试摘要

### Requirement: 任务状态流转

系统 SHALL 在 C1 区分任务 PENDING、RUNNING、COMPLETED、FAILED、UNKNOWN、SKIPPED 与 INHERITED。普通任务仅一次 PENDING → RUNNING 领取，随后根据已确认结果进入 COMPLETED、FAILED 或 UNKNOWN；依赖阻断的未执行任务进入 SKIPPED。INHERITED 是新 retry 中对原成功前缀的引用，不代表本次执行。NEEDS_REVIEW 是 Run/操作的待复核状态，不冒充当前 task 终态。C1 不将失败或未知任务自动退回 PENDING。

#### Scenario: 任务执行成功

- **GIVEN** 一个符合仓库队列资格的 PENDING 任务
- **WHEN** 请求线程以 CAS 领取
- **THEN** 记录 RUNNING、startedAt 与唯一 claimToken
- **AND** 只有动作证据与结果事务提交成功后，才记录 COMPLETED 与 finishedAt

#### Scenario: 继承前缀不作为新执行

- **GIVEN** retry 中某动作由原 Run 的成功连续前缀继承
- **WHEN** 任务列表被查询或执行器领取后继
- **THEN** 该动作保持 INHERITED，不生成新的 claim、开始结束时间或执行 Step
- **AND** 实际执行顺序只由本次真正领取的任务推进

#### Scenario: 任务执行失败重试

- **GIVEN** 本场景为 C2 目标，恢复执行器已实现且计划明确允许有限自动重试，请求已确认未生效、前置条件仍满足
- **WHEN** 当前尝试失败
- **THEN** 记录新的执行尝试与预算，再按后续恢复协议决定是否重新排队
- **AND** C1 不实现此自动回队行为，UNKNOWN 不适用已确认失败的重试规则

#### Scenario: 传输响应丢失

- **GIVEN** 写请求已进入 SEND_STARTED
- **WHEN** 调用线程收到连接中断或缺失响应，且本地结果事务可提交
- **THEN** 任务进入 UNKNOWN 并保留未决 guard
- **AND** 不能按普通 FAILED 策略再次发送；提交前进程硬中断则保留真实持久状态而不伪造该转移

### Requirement: 手动重试任务

系统 SHALL 通过现有 POST /api/v1/runs/{id}/retry 接收必填 Idempotency-Key 与 `items` 原 RunItem UUID 集合；不得把 taskIds、任务序号或窗口/迭代/仓库复合字符串当作选择契约。仅对已确认失败、阻塞或符合依赖条件的跳过项，沿原协议 3 计划创建有来源的新 Run/Item/四动作任务。响应沿用 `ApiResponse<String>` 的新 Run ID；原终态 Run 不变，成功前缀继承而不重复执行。

#### Scenario: 非失败状态不可重试

- **GIVEN** 原 Item 已完成、尚在运行，或属于预览/无协议 3 证据的历史 Run
- **WHEN** 用户请求 retry
- **THEN** 返回状态或证据不允许重试的错误
- **AND** 不改变原 Run，不重复执行成功步骤

#### Scenario: 对 UNKNOWN 调用 retry

- **WHEN** 用户对 UNKNOWN Item 或 NEEDS_REVIEW Run 请求普通 retry
- **THEN** 返回需要核对的阻断原因
- **AND** 不派发外部写请求

#### Scenario: 使用任务 ID 或伪造 Item 选择

- **WHEN** retry 仅提供 taskIds，或 items 中包含 task ID、非原 Run 的 Item UUID、复合业务键
- **THEN** 拒绝受理，不把它们转换成另一个执行范围
- **AND** 不生成新 Run 或外部写入

#### Scenario: 手动重试失败任务

- **GIVEN** 原 Item 的未完成动作已确认失败或阻塞，前置条件仍满足
- **WHEN** 用户使用新的请求 key 和该 Item 的原 UUID 显式重试
- **THEN** 返回有来源关联的新 Run ID，为其建立固定四动作任务
- **AND** 已成功前缀标为 INHERITED，待执行后缀才可领取；原始证据保持不变

#### Scenario: 相同重试请求再次提交

- **WHEN** 相同 actor、原 Run ID、items 与请求 key 再次提交
- **THEN** 返回首次重试的 Run ID
- **AND** 不重新领取任务，不生成第三个 Run

### Requirement: 异步任务执行

系统 SHALL 在 C2-A 的显式 Orchestrate 或精确 Item retry 受理事务提交后返回 202 与 Run ID，由数据库扫描和有界 worker 驱动同一任务执行器；不得保留请求线程同步写入的备用路径。扫描 SHALL 使用有界游标发现当前 WINDOW_ORCHESTRATION 协议、RUNNING/NEEDS_REVIEW 且存在 PENDING 的 Run，资格由既有领取协议判定。worker SHALL 在开始执行后才领取一个任务；提交拒绝、排队取消不改变持久任务，领取后异常不回退 PENDING。C2-A 不重领 RUNNING/UNKNOWN，租约接管属于 C2-B。默认 MANUAL 下 Publish 仅返回业务状态，不生成编排意图；可选 AUTO_ON_ATTACH 仍须另行实现显式开关并复用唯一受理入口。

#### Scenario: 异步执行不阻塞

- **GIVEN** C2-A 的持久扫描已启用，Git 请求被独立 latch 阻塞
- **WHEN** 用户显式触发 Orchestrate 或合法精确 retry
- **THEN** 系统先提交完整持久受理意图，在 Git 完成前返回 202 与 Run ID
- **AND** 后台通过同一任务执行器处理，Git 不占受理或结果事务，用户能用原 Run 查询进度

#### Scenario: 受理后唤醒丢失（C2 目标）

- **GIVEN** Run、封存计划和持久任务已一起提交
- **WHEN** AFTER_COMMIT 唤醒没有执行
- **THEN** C2-A 的数据库扫描仍能发现原 PENDING 并处理意图，无需重新 POST 或生成新的 key
- **AND** 不创建重复计划，不以租约过期为依据重发未决写请求

#### Scenario: 提交队列被拒绝或取消

- **GIVEN** 持久任务为 PENDING，后台线程池已满或正在停止
- **WHEN** 执行命令被拒绝，或排队命令在 worker 开始前被取消
- **THEN** 任务仍为 PENDING，没有领取 token 或 Git 写入
- **AND** 后续扫描可再次发现；不得回退到 HTTP/扫描线程执行 Git

#### Scenario: 不可领取的前页不饿死后续 Run

- **GIVEN** 有界候选页中的 Run 都因同仓 RUNNING/UNKNOWN 前序不能领取，后续页有可执行 Run
- **WHEN** 扫描继续轮转游标
- **THEN** 后续可执行 Run 被发现并推进
- **AND** 不无界加载所有 Run，不复制或跳过既有领取资格规则

#### Scenario: 同 Run 的仓库独立推进

- **GIVEN** 同一 Run 的仓库 R1 正在执行 Git，R2 仍有满足前序条件的 PENDING 任务
- **WHEN** 另一个有界 worker 消费该 Run
- **THEN** R2 可推进，R1 的后继任务不能越过 RUNNING 前序
- **AND** 不用整个 Run 的执行期互斥把跨仓任务串行化

#### Scenario: 扫描拒绝其他执行协议

- **GIVEN** 存在 PLANNED 预览、历史 WINDOW 协议、WINDOW_CLEANUP 或 VERSION_UPDATE 记录
- **WHEN** 后台扫描候选
- **THEN** 它们不被当作当前 WINDOW 四动作派发，不生成猜测的任务

#### Scenario: 已领取后中断仍安全停住

- **GIVEN** 某任务的 RUNNING 和 claimToken 已提交，worker 随后终止
- **WHEN** C2-A 扫描或新进程继续处理持久任务
- **THEN** 该任务不被重新领取或改回 PENDING，同仓后继保持被阻止
- **AND** 其他仓库的合法 PENDING 仍能推进，历史 Git 证据和 guard 保留

#### Scenario: 当前范围变化后找回原回执

- **GIVEN** 页面保存了原 actor、操作、key 和 payload，原请求可能已受理，窗口随后冻结或当前 scope 为空
- **WHEN** 用户取回该原请求的回执
- **THEN** 使用原 key 和 payload 找回同一 Run，不依赖当前勾选，不产生新 key
- **AND** 新意图仍完整校验当前范围；已确认 Run ID 后只读查询，查询失败不再次 POST

#### Scenario: 不确定回执不能被新选择静默覆盖

- **GIVEN** 同一主体仍保存一个尚未确认的请求，或已确认 key 对应 Run A
- **WHEN** 用户改变选择，或响应把同一 key 映射到 Run B
- **THEN** 未确认意图必须保留并提供明确处理入口，不因网络错误自动创建新意图
- **AND** 同 key 不同 Run 被显示为契约异常，保留原 Run A，不自动重试写入

#### Scenario: 首次明确拒绝与未知回执分开处理

- **GIVEN** 当前页面刚生成新 key 并首次发送原请求，后端返回 HTTP 400 / RUN_006
- **WHEN** 原页面代次、主体和窗口或 sourceRun 仍当前，且该 key 尚无确认 Run ID
- **THEN** 仅清除匹配的本地待确认记录，保留最近已确认回执，修改范围后仍须明确确认新意图
- **AND** 已保存请求的重发、取回后的 400、409、5xx、断网或超时均不能据此删除原请求

#### Scenario: 迟到响应不改变当前请求的含义

- **GIVEN** 旧页请求仍在途，新页已使用同 key 取回或正在显示该请求的确认框
- **WHEN** 旧页迟到收到成功回执或明确拒绝
- **THEN** 成功只能确认匹配记录；新页确认后重新读取该 key 的 receipt，不生成新 key 或重复 POST
- **AND** 已离开或换代页面的拒绝不能清除新页正在取回的同 key，即使新页 202 尚未返回

#### Scenario: NEEDS_REVIEW 下仍观察其他仓库

- **GIVEN** R1 的未知结果使 Run=NEEDS_REVIEW，R2 仍有 PENDING/RUNNING 任务
- **WHEN** 页面观察原受理 Run
- **THEN** 风险提示与其他仓库进度同时保留，只读查询继续，不自动 retry/reconcile/resume
- **AND** 所有任务终结后停止观察；任务查询失败或短暂不一致不得被显示为执行成功

#### Scenario: 观察有界且不污染页面生命周期

- **GIVEN** 页面正在观察已确认的 Run，下一次查询只在上一次完成后安排
- **WHEN** 路由切换、卸载、主体变化、页面进入后台或观察达到时限
- **THEN** 不再排下一次查询，旧响应不能覆盖当前页面或改变当前主体的请求
- **AND** 暂停观察不改变业务状态、不清除原回执；刷新不重置当前选择，不被另一条最新 Run 替换
- **AND** Run 与 Task 两次读取跨越提交点时，RUNNING Run 与已完成 Task 的短暂快照差异仍能在页面恢复可见后继续核实
