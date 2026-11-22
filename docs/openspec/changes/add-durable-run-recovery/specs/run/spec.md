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

C2-B4 已交付，分阶段推送与本片固定完整 SHA 5/5 证据见 tasks 与任务记录 17.5。系统 SHALL 复用原 Task、操作账本和结果事务，对完整到期的原 TRY_MERGE/MERGE_MR/APPLIED 补历史 MERGED 结果；即使整个 Run 没有 PENDING 后继也 SHALL 被默认 worker 发现。恢复 SHALL 零 HTTP，不授予 INTENT/SEND/UNKNOWN 或其他执行类型恢复权限。

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
- **THEN** 候选查询不领取或更换 token，不复制 JSON 证明；原投影入口在 Run→Task→guard 锁内重核完整到期、实际顺序、仓内前序、唯一原 operation/同 attempt sequence 2、token/session/generation、项目、原 PUT/suffix/SHA、计划与检查点
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

### Requirement: 原 MERGE 未发送意图的安全续发

C2-B5 已交付：独立 RED、本地门禁、分阶段推送及固定后端完整 SHA 的 Full-Link 44935216788 为 5/5，事实见 tasks 与记录 18.9。下面两个前置读取 Scenario 明确保留 B5 交付时的限制；其本地结果收口由 B6 独立定义和验收。系统 SHALL 仅复用原过期 TRY_MERGE 的完整未发送 MERGE_MR 意图，分开 Task 当前发送/结果权与 op/guard 历史 reservation，不新增操作、attempt、实体、表、API、DTO 或 dispatcher。

#### Scenario: 唯一原领取入口内接管完整未发送意图

- **GIVEN** 当前协议活动 Run 的原 TRY_MERGE 为完整到期 RUNNING、实际顺序及仓内前序有效、无当前 Step，且只有一个 MERGE_MR/INTENT_RECORDED，其原 attempt 只有完整 sequence 0、未出现任何 SEND 或响应
- **WHEN** 原 claimNextTask 在 Run→Task→guard 锁内刷新数据库时间、原项目/remote/计划/checkpoint/request 与 reservation 后取得 CAS 权限
- **THEN** 原领取入口唯一调用 MANDATORY prepareUnsentMergeClaim，以 PESSIMISTIC_FORCE_INCREMENT 即时递增 operation observation version，并在同一物理事务 CAS 只更新 Task 当前 token/lease；CAS 失败 SHALL 抛错并回滚版本变化，保留原 startedAt、实际顺序、业务身份及原 op claim_token/session/attempt、guard session/operation/generation 与事件
- **AND** 非完整到期、错误 owner/guard/checkpoint、其他动作或状态、已有 Step/SEND/响应均不接管；候选发现不能代替锁内授权

#### Scenario: 原请求的完整 typed 持久化与精确重建

- **GIVEN** 原 target 保留 method/suffix 等既有身份及请求体 sha、merge_commit_message、should_remove_source_branch 三键
- **WHEN** 正常发送或恢复验证原请求
- **THEN** sha 为冻结的合法文本 SHA，消息保留原输入文本，删除标志为布尔 false；请求无 query，空 ? 也拒绝
- **AND** B5 自动续发另要求持久消息精确等于原计划默认 `Merge {source} into {target}`；该资格不推广为正常非 Task 调用必须拥有计划，也不替换其原自定义消息
- **AND** 共用固定字段次序、固定类型且覆盖原 envelope 的指纹；缺失/未知键、错类型、篡改消息/标志、错路径/SHA 或旧 hash 均拒绝且零远端写，不补默认值或兼容回填
- **AND** 复用现有 hash 能力；恢复走原 workflow/adapter/interceptor，begin 返回原 operation ID 且 interceptor 实际使用该 ID，不创建新 operation 或 attempt

#### Scenario: 新接管先赢后旧 worker 无发送或结果权

- **GIVEN** 旧 worker 已提交完整 INTENT 后暂停，原领取到期，新 worker 先在锁内接管并成为当前 Task owner
- **WHEN** 旧 worker 恢复 sending，或将发送拒绝异常映射成 UNKNOWN 后调用 completeTask
- **THEN** 两者均被旧 fence 拒绝，不提交 SEND、UNKNOWN Step 或释放 guard；当前 owner 仍可沿原操作提交实际结果
- **AND** 两名新 worker 竞争最多产生一个当前 owner；sending/finish 显式核实际 caller TaskFence，非 Task 调用显式无 TaskFence，不保留无 fence 的旧重载旁路

#### Scenario: 旧 SEND 先赢后保留实际响应

- **GIVEN** 旧 worker 的 SEND 事务先取得原锁并提交，或 SEND 已提交而 HTTP 尚未开始
- **WHEN** 新 worker 在锁内刷新后尝试接管
- **THEN** 不更换 Task token/lease、不再次发送；旧实际 sender 的迟到 HTTP finish 仍可保存，不能因 lease 到期丢弃已经发生的观察
- **AND** 新 owner 已获授权后的旧 finish 不得冒用 operation ID 覆盖事件；finish 只认可该 attempt 已记录的实际 sender

#### Scenario: 实际 sender 与原历史 reservation 各自承担职责

- **GIVEN** 接管后的 Task token 不同于原 op claim_token/session，而 guard 仍绑定原 session/operation/generation
- **WHEN** 当前 worker 发送、完成或确认原操作
- **THEN** sequence 1 记录实际 sender 的 taskId/claimToken，finish、ack 及本片后续 APPLIED 证明按原 attempt 的 sender 和当前 Task 核权；B2/B3 原 token 资格不放宽
- **AND** 原 session 等值映射保护、非 Task completeSession 与历史事件归属保持，不为接管重写历史 reservation
- **AND** MERGE 缺 typed 请求或旧 hash 不得自动续发；历史 APPLIED 投影按完整成功响应、原 target 的 method/suffix/SHA 与同 attempt sequence 1 的实际 sender 核证明，不要求补齐未参与投影职责的消息/hash
- **AND** 旧 sequence 1 空对象不能获得投影资格；保留历史但不以原 op token 补造 sender，不设兼容回填

#### Scenario: 接管使旧观察版本失效但保留合法历史观察

- **GIVEN** 只读观察基于接管前 operation version 发起，接管后另有基于新版本的合法观察
- **WHEN** 两者按历史 Task 关联与 expectedVersion 提交
- **THEN** 旧版本被拒绝，新版本按原规则核对并可保存；不使用原 op token 与当前 Task token 等值来替代观察资格

#### Scenario: B5 交付时前置读取不能确认继续发送的保守边界

- **GIVEN** B5 交付基线 0642828 的原未发送意图已接管
- **WHEN** 前置 GET 失败、MR 已 closed 或 source 已由 A 变为 B
- **THEN** Task 可落 UNKNOWN，但 operation 保留 INTENT 且无 SEND/响应，guard 的原 reservation 与 INTENT_PENDING 理由保持；不得误写 REMOTE_RESULT_UNKNOWN、新增 guard 状态或伪造 APPLIED/REJECTED
- **AND** 只有原 checkpoint 仍 ready 且精确请求有效时才能发送一次 PUT

#### Scenario: B5 交付时已满足或阻塞结果尚不能自动收口

- **GIVEN** B5 交付基线 0642828 的原未发送 MERGE 意图已接管，前置 GET 证实 target 已包含原 A 或返回 BLOCKED
- **WHEN** 原 workflow 尝试通过 completeTask 提交该读取结果
- **THEN** 不伪造 APPLIED/REJECTED 来消费 INTENT；允许原结果事务整体拒绝回滚并保留 Task RUNNING、operation INTENT 及 guard 原 reservation/INTENT_PENDING
- **AND** 这是 B5 已验收的历史边界；B6 另行定义本地收口，不将 B5 测试当作 B6 已通过的证据

#### Scenario: 真实竞争、末尾发现与进程中断验收

- **GIVEN** 坏候选占满前页、后页有合法末尾 INTENT，或原 MERGE INTENT 已提交而任何 SEND 未发生
- **WHEN** 有界 keyset 扫描，或独立 JVM 在 INTENT 后 exit=137 并到期重启
- **THEN** 原 worker 继续发现后页候选且锁内授权，同仓不越序、跨仓可推进；合法重启复用原 operation/attempt，PUT 总数为 1，首次时间/顺序/历史 reservation 保持
- **AND** 独立验收包含真实 PG 锁两种胜出顺序、两名新 worker、输家 UNKNOWN、显式 fence/观察 version，以及 B2/B3/B4 回归；未发送以外的 INTENT 动作、SEND/UNKNOWN、cleanup/version 与完整 UI 不扩入本片
- **AND** 回滚停止派发并保留原事实及保护，不撤销已发生的远端写入

### Requirement: 原 MERGE 未发送意图的本地结果收口

C2-B6 已 DONE：独立契约、RED→GREEN 与本地门禁完成，实现 4e992e9、文档 29ec005 已推送，固定实现完整 SHA 的 Full-Link 44940092094 为 5/5，事实见 tasks 与记录 19.9。系统 SHALL 仅以当前 Task fence、原完整 MERGE INTENT、同 attempt 严格唯一 sequence 0 且无 SEND/Step，以及同物理事务的 NOT_SENT 与 Task 终态证明释放原 reservation；不得使用 BlockReason 白名单或业务读取是否确定代替未发送证明。

#### Scenario: 原结果事务内记录未发送事实

- **GIVEN** 当前 RUNNING TRY_MERGE 具有完整领取、唯一原 MERGE_MR/INTENT，原 request/hash、CREATED/MR_REUSED checkpoint、guard 与 no Step 满足 validUnsentMerge；当前 fence 属于未过期原 owner 或 B5 到期换权后的 owner
- **WHEN** 原 completeTask 接收真实 ALREADY_MERGED、MERGE_BLOCKED 或 UNKNOWN 结果与实际 patch，收口本身不要求 lease 再次到期
- **THEN** acknowledgeTask 在同一物理事务的 Step/metadata 写入之前复用原完整资格，记录 NOT_SENT 状态及同 attempt sequence 1 本地事件，并原子提交 Task/Step/patch/队列/绑定/Run 聚合及原 guard 释放
- **AND** 原 operation ID/attempt/sequence 0/claim/session、身份与历史事实保持；事件必含当前 taskId/claimToken/result，evidenceJson.metadata 保存真实 nullable patch，operation/event reason 取实际 git.blockReason、无则 null
- **AND** NOT_SENT 不新增 GitWriteResult.Outcome，不伪造 HTTP status、响应或 requestId，不复制原完整请求/身份，不增加实体、表、DTO、API、dispatcher、provenance 或兼容回填

#### Scenario: 本地结果与实际 patch 的语义一致

- **GIVEN** validatedPatch 已校验字段集合与身份，但这不能证明结果语义
- **WHEN** 未发送意图尝试提交本地结果
- **THEN** ALREADY_MERGED 必须对应 patch.mrDisposition=ALREADY_MERGED；UNKNOWN/MERGE_BLOCKED 不得带成功 mergeSha 或成功 disposition；null 仍表示原字段删除
- **AND** 异常路径仅携带 blockReason 时不强制补 requestId 键；拒绝未发送却 MERGED，不把 FAILED 纳入本片本地收口

#### Scenario: 已包含原提交的读取结果保持原证明边界

- **GIVEN** 原 target 已证明 contains 冻结 source A，或 MR 虽为 merged 但 target 已 reset
- **WHEN** 原 workflow 解释前置读取结果
- **THEN** contains A 足以按原规则返回 ALREADY_MERGED，不额外要求 MR 读取成功，MR 404 不推翻既有 contains 证明；原 mergeSha 的额外 contains 要求保持，不补造 SHA
- **AND** MR merged 而 target 不再包含原提交时仍 UNKNOWN，不把 MR 状态替代 target 证明

#### Scenario: 无发送事实的释放不改变业务阻断与重试

- **GIVEN** GET 403、其他真实 BLOCKED，或 unavailable/closed/sourceChanged 的 UNKNOWN，均具备原完整未发送证明
- **WHEN** NOT_SENT 与本地结果原子提交
- **THEN** GET 403 保持 BLOCKED/PERMISSION_DENIED；UNKNOWN 保持 Task UNKNOWN/Run NEEDS_REVIEW，同仓后继沿原规则 SKIPPED，领取 token/开始时间为空且无后继 Step，禁止自动或普通重试
- **AND** BLOCKED 沿原 FAIL_FAST/CONTINUE_ON_BLOCK 汇总和用户重试语义，重试复用原 MR；释放 reservation 不扩大 UNKNOWN 的执行或重试权

#### Scenario: SEND 与本地收口互斥并保留真实响应

- **GIVEN** interceptor 在独立 sending 事务提交后才执行 HTTP，本地收口与发送竞争同一原锁
- **WHEN** SEND_STARTED 先提交
- **THEN** MERGE SEND 已提交但尚无 sequence 2 响应时，包括同 fence UNKNOWN 在内的任何本地 completeTask 均整笔拒绝，保留真实发送者迟到 finish 的权利；即使 HTTP 尚未开始也不得将 SEND 改成未发送
- **AND** operation 已 UNKNOWN 且有真实 sequence 2 响应时，既有 UNKNOWN 确认继续允许；本条不扩展 SEND 恢复
- **AND** 若 NOT_SENT 先提交，旧/现 worker 的迟到 sending、finish、complete 均拒绝；两名收口者最多产生一个 NOT_SENT 事件与一个 Step

#### Scenario: 资格错误与完整结果回滚

- **GIVEN** stale fence、缺 typed 请求、旧 hash、错 checkpoint/guard、非唯一原 sequence 0 或已有其他事件
- **WHEN** 本地结果进入 acknowledgeTask
- **THEN** 零修改拒绝，不以候选发现、业务分类或兼容补值获取资格
- **AND** ack 后外层事务回滚或最终 Task CAS 实际返回 0 时，operation state/version/事件、Task/Step/metadata/队列/绑定/Run/guard 全部回滚；两种真实数据库证据分别验收

#### Scenario: 观察版本与当前 remote 保护保持各自含义

- **GIVEN** 观察使用 NOT_SENT 前旧 expectedVersion，或同 remote 后来存在另一操作的 guard
- **WHEN** 原操作收口后观察或读取 Operation
- **THEN** 旧版本观察拒绝，新 observe 仅保存观察审计与 operation 更新时间/版本，不推进执行、不改写原事实或 guard；仅清匹配原 session/operation/generation 的 reservation，其他 guard 保留，mapping/baseURL 的 hasActiveGuards 仍按真实保护生效
- **AND** Operation.blocked 继续表示 remote 当前是否有任一 guard，历史 NOT_SENT 可为 blocked=true，不硬设恒 false

#### Scenario: 默认 worker 的进程恢复与仍 ready 的请求

- **GIVEN** 独立 JVM 在原完整 INTENT 后强杀，或原 checkpoint 仍 ready
- **WHEN** 默认 worker 到期后恢复无 PENDING 的末尾原任务
- **THEN** 已满足/阻塞/UNKNOWN 的本地结果以 0 PUT 收口并保持原身份与历史；仍 ready 的请求沿 B5 正常路径一次 PUT
- **AND** 只恢复本片原 MERGE 未发送意图，不扩大其他 INTENT 动作、SEND/UNKNOWN 或 cleanup/version 的恢复范围

#### Scenario: 现有 Run 与 Repository 消费者显示未发送状态

- **GIVEN** RunDetail 与 RepositoryDetail/RepositoryDrawer 使用既有 GitOperationsPanel
- **WHEN** 列表或详情包含 NOT_SENT 状态和事件
- **THEN** 现有 zh/en 标签与组件展示该本地事实，blocked 仍据实际返回显示，不伪造远端响应或增加动作入口
- **AND** 以组件验收证明真实消费者；完整 UI 黄金旅程仍需独立验收，state/event 字符串无需新增 schema 迁移

### Requirement: 原 CREATE_BRANCH 未发送意图的安全接续与本地收口

C2-B7 已 DONE，验收证据见任务记录第 20 节，当前任务以执行路线图为准。系统 SHALL 只在 ENSURE_FEATURE、ENSURE_RELEASE 的原完整 CREATE_BRANCH/INTENT 具有当前 fence、匹配 guard、同 attempt 唯一 seq0、无 SEND/自身 Step 时沿原 Task 接续或本地结束，不因快照、到期或任意 FOUND 授权写入。

#### Scenario: 原准备路径交付既有操作快照但不交付绕过权限

- **GIVEN** 原 CREATE_BRANCH 满足完整未发送资格，或既有 MERGE 仍满足其各自资格
- **WHEN** 原 prepare 原位重命名并由唯一领取路径调用
- **THEN** 返回 Optional<Operation>，现 TaskClaim 携带 nullable 已有 Operation，metadata/plan 不被补写或替换
- **AND** Context 在 provider 预检前绑定原数值项目 I，bindProjectId 保留快照；prepare/begin/sending 仍分别按当前 DB fence/guard/无 SEND 证据核对，不把快照当作权限
- **AND** 不增加平行 prepare、查询接口、持久实体、DTO 或兼容通道；Operation.version 不成为独立权限，不增加发送 expectedVersion 参数
- **AND** 仅 observe 增长审计版本而原意图/fence/guard/target 仍合法时可继续，只有实时执行事实失效才拒绝；既有观察版本校验保持

#### Scenario: 原分支命令的严格 canonical 请求被实际发送

- **GIVEN** typed target 严格包含 method=POST、suffix=/repository/branches、branch、ref、sourceRef，sourceRef 匹配原计划，ref 为原 40/64 位 SHA
- **WHEN** 首次请求或原操作接续经过 begin
- **THEN** begin 原位返回已核准的已有 Operation，唯一 interceptor 消费其 target 构造 body；不保留 String 返回兼容入口
- **AND** 实际 POST 只有 branch/ref，canonical 次序为 branch、ref，使用 UTF-8 并保留 ref 原始大小写，URI 无 query 组件
- **AND** requestHash 等于 SHA256("POST|" + remoteKey + "|/repository/branches||" + SHA256(canonicalUtf8Body) + "|")；规范化由现 GitLabGitBranchAdapter 承担并供 interceptor/Journal 消费，不记录 secret、不加通用请求实体或旧/新 hash 双轨

#### Scenario: sourceRef 前进或删除不改变原冻结提交

- **GIVEN** 原 INTENT 已冻结 SHA A，原 sourceRef 随后前进至其他提交或被删除
- **WHEN** 当前 owner 沿完整未发送证明继续创建分支
- **THEN** 实际 POST ref 仍为 A，新增 sourceRef 解析为 0，sourceRef 只与原计划匹配
- **AND** 不从当前符号引用重新生成请求、不改原 operation/attempt/seq0 或冻结计划

#### Scenario: namespace 重映射不能把原意图带到另一个项目

- **GIVEN** 原操作数值项目为 I，而当前 namespace 解析为 J
- **WHEN** 原任务重新进入 provider 预检
- **THEN** Context 已携带 I，现 resolver 按 expected I 拒绝，J 的 branch GET/POST 均为 0
- **AND** 保留真实 FAILED 分类，不为本地收口硬转 UNKNOWN；结果 patch 若含 git.projectId，只能为 I

#### Scenario: 合法本地结果原子结束未发送分支意图

- **GIVEN** 当前完整未发送 CREATE_BRANCH 得到真实 ALREADY_EXISTS、FAILED 或 UNKNOWN，实际 patch 为空、仅 git.projectId 或含可选 git.blockReason
- **WHEN** 原 completeTask/acknowledgeTask 接受该本地结果
- **THEN** 原操作 NOT_SENT、同 attempt seq1 本地事件、Task/Step/metadata/队列/绑定/Run 与匹配 guard 同物理事务提交，原 owner/session/attempt/seq0 保持
- **AND** project patch 若提供必须为原 I，不强制合并专属成功字段或补 blockReason；无 SEND 却 CREATED、错项目或结果身份污染被拒绝
- **AND** 后继、普通 retry 与 UNKNOWN 的既有限制保持，不因释放已知未发送 reservation 而扩大重试权

#### Scenario: 已有目标分支可以不同于原创建基点

- **GIVEN** 原 INTENT 准备从 A 创建目标分支，而预检确认目标已存在于合法 B，B 可不等于 A
- **WHEN** 当前 owner 按 ENSURE 语义完成
- **THEN** 以零创建 POST 收口 NOT_SENT/ALREADY_EXISTS，不把分支改回 A
- **AND** 不用本次 HTTP 201 创建成功证明否定合法 ALREADY_EXISTS，也不伪造 CREATED

#### Scenario: 类型、指纹和原始资格缺口不能由快照补齐

- **GIVEN** target 缺字段、类型不符、多字段/query、canonical hash 不匹配，或 fence/guard/attempt/事件/自身 Step 不符合原资格
- **WHEN** prepare、begin、sending 或本地完成复核
- **THEN** 拒绝相应换权、发送或收口，不回填缺失事实、不消费快照绕过实时 DB 保护
- **AND** ENSURE_FEATURE/ENSURE_RELEASE、same/new owner 和 40/64 位 SHA 分别定义正反例；AUTO/NAMED/EXISTING 保持既有模式，不制造非法创建 INTENT

#### Scenario: begin 与 SEND 之间的真实竞争不产生迟到写入

- **GIVEN** 一名 owner 已获得 begin 的 Operation，另一方在 sending 前完成换权、NOT_SENT 或赢得 SEND
- **WHEN** 原 owner 或重复本地收口者继续执行
- **THEN** 真实数据库锁和当前 fence 拒绝迟到 sending/finish/complete，原快照不授予 HTTP 权限；最多一名有效 sender 或一个 NOT_SENT/Step
- **AND** CREATE_BRANCH/SEND_STARTED 尚无 seq2 时拒绝提前本地 UNKNOWN，保留真实 finish；已有真实 seq2 UNKNOWN 仍保留 guard，不自动重发

#### Scenario: 领取和结果原子性通过真实数据库回滚验证

- **GIVEN** prepare 或结果事务已形成对应的版本、NOT_SENT/事件和结果变化
- **WHEN** 外层事务回滚，或实际最终 Task UPDATE 返回 0
- **THEN** 对应 operation/version/事件、Task/Step/metadata/队列/Run/guard 整体回滚
- **AND** 真实 PG 锁等待与 CAS 拒绝证据必须证明执行到相应阶段，不以提前资格拒绝代替回滚证明

#### Scenario: 换权发送后的 APPLIED 按实际 sender 恢复原分支结果

- **GIVEN** 原 INTENT 换权后由当前 owner 发送，真实 seq1 已记录 sender，原 CREATE_BRANCH/APPLIED 的 201/name/SHA 响应证明完整
- **WHEN** APPLIED 后、业务结果前再次强杀并由第三 JVM 恢复
- **THEN** 按真实 seq1 sender 核对原 Task，恢复阶段 HTTP=0；原 op.claimToken/session 仍是历史归属，不要求改写为新 owner
- **AND** 保持 B2 既有 201/name/SHA 成功证明，不把本片新 canonical hash 强加到 APPLIED 投影，也不新增 hash 兼容双轨

#### Scenario: 独立进程只恢复有完整未发送证据的原分支命令

- **GIVEN** 独立 JVM 在原 CREATE_BRANCH INTENT 后强杀，或在 SEND 后（即使 HTTP 尚未到达）强杀
- **WHEN** 默认 worker 重启扫描
- **THEN** 完整未发送意图只沿原操作续发一次 branch POST，随后 APPLIED 再强杀的第三 JVM 零 HTTP 补原结果
- **AND** 已 SEND/UNKNOWN 不自动重发；回归 B2–B6、后继及普通 retry/观察保护
- **AND** CREATE_MR 原摘要不含完整 title/description/源分支删除选项 且含随机 marker，不能借本片宽松续用；其他动作和完整 UI 另行验收

### Requirement: 正常 CREATE_MR 完整请求的发送证明闭环

C2-B8 已 DONE，以下契约经独立 RED、一次 PG 过强断言校准及本地 125/125 与相关 965/965 验证；实现 `537588e` 已推送，固定 SHA CI 44972473174 attempt 1 为 5 PASS / 0 FAIL。B9 原 INTENT 另作独立验收。系统 SHALL 沿现 producer → adapter/interceptor → Journal begin/sending → 实际 HTTP → B3 响应 → 原结果消费者证明正常 CREATE_MR 发送了已接纳的完整请求；同时覆盖 ENSURE_MR Task 及现 cleanup/GitMergeWorkflow 非 Task producer，不新建实体、schema、DTO、Port 或用户入口，该正常发送契约本身不授予 CREATE_MR 接管或重放权。

#### Scenario: 正常 producer 提供严格五字段请求

- **GIVEN** 现 GitLabMergeRequestAdapter.create 由 ENSURE_MR 或非 Task cleanup 调用
- **WHEN** 构造正常 CREATE_MR
- **THEN** 正文严格只有非空白字符串（保持原值、不 trim） source_branch/target_branch/title、字符串 description、布尔 false 的 remove_source_branch；原 producer 显式提供 description 空字符串
- **AND** description 不能 null，缺字段/错类型/多字段在 SEND 前拒绝，不默补、不保留旧四字段入口；false 维护共享 feature 不得擅删

#### Scenario: 受控 marker 唯一并可由现 observer 整行匹配

- **GIVEN** 入站 description 不含 reserved ReleaseHub-Operation: 前缀，候选 opId 非空白且不含 CR/LF，保持原值、不 trim
- **WHEN** interceptor 为新鲜 UUID opId 追加 `\nReleaseHub-Operation: <id>`
- **THEN** recorded description 的 marker 是完整独立末行、精确对应本 opId，reserved 前缀只出现一次
- **AND** foreign/重复前缀、错误末行、空白或含 CR/LF 的 opId 均拒绝，不静默清洗/去重；合法 Unicode/引号/换行按原值保留
- **AND** 现 observer description.lines().anyMatch(marker::equals) 的整行精确匹配保持，FOUND 不授权发送或完成

#### Scenario: 完整 typed target 与 canonical 指纹一致

- **GIVEN** 合法五字段正文及严格 40 或 64 位 hex expectedSha
- **WHEN** 记录 CREATE_MR 命令
- **THEN** target 严格为 method=POST、suffix=/merge_requests、五个 typed 正文域及 sha 共八字段，sha 字节与大小写保留
- **AND** canonical UTF-8 body 固定 source_branch、target_branch、title、description、remove_source_branch 次序，不额外 trim 或清洗
- **AND** hash 为 SHA256("POST|" + remoteKey + "|/merge_requests||" + SHA256(canonicalBodyUtf8) + "|" + expectedSha)，不改变 MERGE 既有规则
- **AND** 任意 query（含空 ?）、错误 hash/字段/marker 均拒绝；本地 sha 不成为创建 POST 的原子前置条件

#### Scenario: begin 返回的 Operation 约束实际 HTTP body

- **GIVEN** begin 仍返回已有 Operation，hash/session/fence 由 Journal 实体负责
- **WHEN** interceptor 消费返回值
- **THEN** 核候选 id/action/repo/remote/provider/base/project/target/run/operator、INTENT_RECORDED 状态和 null reason，并从返回 target 重建实际 body
- **AND** 错误 id/target/其他可见身份导致零 SEND/POST，不沿原入站 body 继续；Operation 不加 hash/session/fence 展示字段
- **AND** 快照不授权发送，不新增 expectedVersion 发送门禁

#### Scenario: 当前 Task sending 实时核完整实体证明

- **GIVEN** begin 已核准正常 ENSURE_MR 请求
- **WHEN** 当前 owner 进入原 sending 事务
- **THEN** 锁内重新核 typed target/hash/marker、op.reason=null、GITLAB/canonical base/正整数 project 与 remoteKey 自洽以及真实 session/guard
- **AND** 原 ENSURE_MR source/target、consistentProject、当前 fence、原历史 owner、同 attempt 唯一 seq0、无 SEND/自身 Step、匹配 INTENT_PENDING guard 均成立
- **AND** begin 后实体破坏、换权、错 guard 或迟到 owner 均零发送；合法 guard 不替代完整实体证明

#### Scenario: 非 Task cleanup 正常写继续消费完整请求

- **GIVEN** executeCleanup → executeCleanupItem → executeMerge → GitLab mergeBranch → GitMergeWorkflow 使用同一个 create，taskFence=null
- **WHEN** 正常 Journal.begin/sending 及真实 HTTP 执行
- **THEN** 完整 target/hash/marker、现 session/guard 和实际 HTTP body 同样一致，不误拒绝合法非 Task 创建
- **AND** Task metadata/计划/fence/无自身 Step 只对 Task 追加；复用现发送事务，无额外 GET 或网络持锁

#### Scenario: Journal 自身事务失败只回滚本事务新增变化

- **GIVEN** 正常 CREATE_MR 已进入 Journal admission/sending 自身事务
- **WHEN** 该事务失败，sending 反例真正经过 seq1/guard 暂写后抛错
- **THEN** 只回滚该未提交事务内的新变化，零非法 SEND/POST，保留此前已独立提交的事实
- **AND** 旧 fence/owner、事件/自身 Step 污染仍被拒绝；真实 PG 锁等待与回滚反例证明实际经过相应阶段，不以前置拒绝冒充

#### Scenario: 外层业务回滚保留独立提交的写入事实

- **GIVEN** INTENT、SEND 或响应事实已通过 REQUIRES_NEW 独立提交
- **WHEN** 外层业务事务回滚
- **THEN** 已提交事实保持，不把已知 APPLIED 改说远端 UNKNOWN
- **AND** Task 沿既有结果确认/APPLIED 投影边界，非 Task 沿既有会话收口保持必要 guard
- **AND** 不因回滚自动重放，不抹去已发生的 HTTP 证据

#### Scenario: B8阶段的完整 CREATE_MR INTENT 尚无恢复资格

- **GIVEN** 在B8交付基线中，完整 CREATE_MR/INTENT 已持久化且 Task 期限已到
- **WHEN** 默认 worker、prepareUnsentTaskClaim 或 RunJpa 领取路径检查候选
- **THEN** 0 token 换权、0 SEND、0 Step、0 POST
- **AND** validUnsentOperation 恢复白名单、Context resume 快照和 NOT_SENT 不扩大到 CREATE_MR；B9 另行定义 workflow 按原 A 决策及真实本地结果

#### Scenario: B8阶段原 owner 主动报告 UNKNOWN 尚非恢复或 NOT_SENT

- **GIVEN** 在B8交付基线中，原 owner 仍有当前 Task fence，完整 CREATE_MR INTENT 只有唯一 seq0、未 SEND
- **WHEN** 原 owner 沿既有失败消费者主动确认 UNKNOWN
- **THEN** 原 Task 与本地 Step 为 UNKNOWN，原 operation 仍 INTENT_RECORDED、seq0 与 token 不变，guard 保留 REMOTE_RESULT_UNKNOWN
- **AND** 无 SEND/POST、不标 NOT_SENT，不授权后继或普通 retry；该路径不同于到期自动扫描/领取

#### Scenario: source 前进反例保留 UNKNOWN 和 guard

- **GIVEN** 创建 POST 前读取 source A，真实创建响应 SHA 已为 B
- **WHEN** B3 原响应证明判定结果
- **THEN** 仍 UNKNOWN/保留 guard，不建 CREATED 检查点、不启动 TRY
- **AND** GitLab 17.11 创建 POST 无 sha-CAS，不能把完整本地请求或一次读取冒充原子前置条件；merge PUT 的 SHA 前置条件保持原语义

#### Scenario: 历史 B3 APPLIED 保持原响应投影

- **GIVEN** 历史 CREATE_MR/APPLIED 的三字段 target/任意 hash 已具有原 201/三处 project 身份/source-target/SHA/IID/URL 成功响应证明
- **WHEN** 原 ENSURE_MR 检查点或已有独立进程恢复执行
- **THEN** 沿原纯 DB 投影补原 Task 及后继消费者，保留原 HTTP 事件；不追加新完整请求/hash 门禁
- **AND** 不把正常请求证明扩大为历史 INTENT、SEND/UNKNOWN 或完整 UI 已验收

#### Scenario: 现 targetJson 返回受支持完整请求并保留隐私边界

- **GIVEN** Task 与现非 Task producer 的 title 均为 `Merge <source> into <target>`，唯一 MR POST 无 description 参数，本片使用空字符串加 marker
- **WHEN** 现 GitOperationController repo/run 列表或详情序列化 targetJson
- **THEN** 该既有字段返回受支持的完整 title/description/布尔值等请求信息，不宣称数据仅在 DB 内部或 API 数据完全不变
- **AND** 现操作查询需认证但无仓库级/操作者级过滤，不声称仅创建者可见，不在 B8 扩 RBAC；GitOperationsPanel 不渲染 targetJson，无新增 UI 入口
- **AND** 原“标题不存”测试断言按完整请求可审计调整，token/认证 header/无关正文及响应证据隐私约束保留，不泛化存任意 body 或 secret；独立 RED 冻结后的契约仍须保留

B9最终验收状态（不替代下列规范）：独立RED、校准与实现后，单次相关1078/1078、API48/48、普通918（913 PASS/5既有SKIP）、静态及最终源码绑定通过；113新例包含于1078。四个排序PG例直接核三个读取入口的完整步骤及末项，每批最多一次Task查询、无关联时零额外查询及completeTask复用约束以源码审查留证，不冒称运行时SQL计数。实现 `666acdc7a60c0922db2f05713cbfefd23eed8265` 已推送，固定该SHA的Full-Link 44978661003attempt 1已completed/success，5 PASS / 0 FAIL，B9已DONE。B10「MERGE_MR 精确只读核对证据」已DONE，typed观察仍不改Task/Step/guard/seq2，最终证据见记录23.11。B11读取状态证明已DONE，契约及终态见下列规范与记录24.9；当前唯一HEAD为B12「无自身写意图任务的失败与重试闭环」，SEND/UNKNOWN受证据约束恢复继续排队。证据与边界见任务记录22.7–22.14；历史失败不覆盖，完整UI未扩验。

### Requirement: 完整未发送 CREATE_MR 意图的原任务接续

系统 SHALL 仅为ENSURE_MR的完整、确未发送CREATE_MR意图接续原任务，复用原Operation/TaskClaim/Context/Journal/workflow并按原项目I与期望SHA A决策。当前fence和数据库内的完整证明决定发送权，原快照只提供值；不将该能力扩大到非Task、旧摘要、SEND/UNKNOWN或其他动作。

#### Scenario: 完整意图在原锁序内取得接续资格

- **GIVEN** 同一ENSURE_MR Task恰有一个CREATE_MR operation，状态INTENT_RECORDED且reason=null
- **WHEN** 原领取或prepare路径按Run→Task→remote锁序重核候选
- **THEN** 必须具备B8严格八字段typed target/canonical hash/唯一原marker、同operation/attempt唯一合法seq0、无SEND及自身Step
- **AND** 原repo/remote/provider/base/正整数项目I、run/operator、历史owner/session/claimToken/generation、原计划source/target、consistentProject与INTENT_PENDING guard均须自洽

#### Scenario: 破损与旧摘要不被补全后接管

- **GIVEN** 意图缺完整字段、hash/marker不符、存在额外operation/event/自身Step，或历史身份与guard不匹配
- **WHEN** 扫描、prepare、begin或sending检查其资格
- **THEN** 拒绝相应接管或发送，零非法SEND/POST，不补造身份、请求、seq0或guard
- **AND** SEND/UNKNOWN及非ENSURE_MR动作不因租约到期或任意FOUND取得本片资格

#### Scenario: 租约只决定换权而原历史身份保持

- **GIVEN** 完整原意图满足资格，当前Task领取事实成立
- **WHEN** 到期的新owner在同事务取得原Task
- **THEN** 只更换Task当前token/lease并推进operation观察version，保留原operation id/attempt/session/claimToken/generation/seq0、首次时间和已有实际顺序
- **AND** 当前owner的发送仍核实时fence，不以租约时钟或snapshot.version替代权限；原历史owner不改写为新sender

#### Scenario: 业务预检前固定原项目和原SourceRevision

- **GIVEN** 现TaskClaim/Context携带已准备的原Operation
- **WHEN** GitLab adapter构造workflow并开始预检
- **THEN** 先固定原项目I，再把已有SourceRevision(I,A)传入ensure方法的nullable参数
- **AND** 现workflow.merge与adapter.ensure两个调用者直接迁移，无旧签名兼容overload；fresh/cleanup传null
- **AND** namespace重映射不得让预检访问错误项目的branch/MR业务端点，网络不持数据库锁

#### Scenario: target已经包含原A时不依赖当前source

- **GIVEN** prepared命令的原I/source/target/A完整
- **WHEN** contains(target,A)已确认true
- **THEN** 直接产生允许无IID/URL的ALREADY_MERGED检查点，sourceSha保留原A字面
- **AND** 零source GET、opened列表、MR详情与CREATE_MR POST，即使source已前进或删除也不另造MR

#### Scenario: 唯一原A的opened MR可以重用

- **GIVEN** contains(target,A)已确认false，完整opened列表只有一个候选
- **WHEN** 候选详情确认原始三个数值项目身份均为I、source/target匹配、SHA与A语义相等且state=OPENED
- **THEN** 返回MR_REUSED及有效IID/URL，prepared检查点保留原A字面，零创建POST
- **AND** 不先要求当前source仍为A；原TRY独立重读MR，并以A约束merge请求

#### Scenario: 列表或详情未证实不能当作空列表

- **GIVEN** contains未确认，或opened列表不完整/多候选，或详情project/source/target/SHA/state不符
- **WHEN** prepared预检判断是否创建
- **THEN** 按既有UNKNOWN/blocked等真实分类停止，零POST，不挑选第一条或将失败结果默认为空
- **AND** 失败context保留原I/source/target/A，不从B或foreign响应借用iid/url/mergeSha/disposition

#### Scenario: 仅确空列表和真实原I/A允许原创建

- **GIVEN** contains已确认false且完整opened列表确为空
- **WHEN** 随后的readSource确认真实项目I及与A语义相等的SHA
- **THEN** 才允许沿原operation继续一次CREATE_MR，不用当前读取值改写原target/hash
- **AND** prepared workflow新生成的检查点保留原A字面，实际正文仍是原五字段，不新增GitLab不支持的sha条件

#### Scenario: 空列表后source前进删除或读取失败不创建

- **GIVEN** prepared路径已确认target不含A且opened列表确空
- **WHEN** source已为B、删除或读取未知，或当前读取明确权限拒绝
- **THEN** B/删除/读取未知形成UNKNOWN，权限拒绝保持既有blocked分类，零POST
- **AND** 原I/source/target/A保持，不能把新的B作为原意图续发或用未证实MR字段形成成功检查点

#### Scenario: 详情读取先核原始三处项目身份

- **GIVEN** GitLabMergeRequestAdapter.read收到MR原始响应
- **WHEN** 映射为已有MergeRequest
- **THEN** 在映射前核project_id/source_project_id/target_project_id的原始数值身份并全部对应预期I
- **AND** prepared重用再核OPENED/source/target/A；fresh/cleanup原有mergeSha补充语义保持，不为本片删除现返回字段

#### Scenario: prepared发送沿原id和唯一marker重建正文

- **GIVEN** 原完整operation已由当前fence准备，原id/body/marker/hash保持
- **WHEN** interceptor执行begin并取得已有Operation
- **THEN** 不生成替代id或追加第二个marker，核返回身份/状态/target后从返回值重建原五字段HTTP正文
- **AND** sending在现数据库锁内实时重核当前权力与完整证明；换权、收口或破损后的旧快照零非法SEND/POST

#### Scenario: 新sender的真实事件支持后续纯DB投影

- **GIVEN** 新owner安全接管原意图后，原历史owner/session/attempt/seq0未改
- **WHEN** 实际SEND、finish、ack或APPLIED后的恢复执行
- **THEN** seq1记录真实新sender，finish/ack/APPLIED投影核该seq1与当前资格
- **AND** INTENT后强杀、接续APPLIED后再强杀的第三JVM仅补原结果，投影HTTP=0，全场景MR POST总数1，原body/marker和期望A保持

#### Scenario: 原B3响应投影与已发送诊断边界保持

- **GIVEN** 历史B3 APPLIED有三字段target/任意hash及完整原201/三处项目/source-target/SHA/IID/URL响应证明
- **WHEN** 原ENSURE_MR结果投影执行
- **THEN** 不新增完整请求/hash门禁；SHA按原ignoreCase语义验证并保留响应SHA字面，不要求强行改成原A字面
- **AND** 真实sender仍核seq1；B3已SEND后failedWrite的部分IID诊断不因prepared预检规则被删除

#### Scenario: 非Task与创建后源竞争仍沿现有安全边界

- **GIVEN** 非Task cleanup正常调用，或合法预检读A后原POST的实际响应已为B
- **WHEN** 正常B8请求链及B3响应消费者执行
- **THEN** 非Task继续正常完整请求且不获得接管资格；响应B仍UNKNOWN/guard保留、无CREATED检查点或后继TRY
- **AND** 创建POST没有sha-CAS，完整本地命令或一次读取都不能替代远端原子条件

### Requirement: 未发送 CREATE_MR 的真实本地结果原子收口

系统 SHALL 仅在完整原CREATE_MR意图、当前fence、唯一seq0且无SEND/自身Step的证明下，把实际MR_REUSED、ALREADY_MERGED、FAILED、MERGE_BLOCKED或UNKNOWN作为NOT_SENT与原Task结果同事务收口。该本地结论不声称远端创建成功，不允许CREATED、非空git.requestId或矛盾身份；UNKNOWN的后继与普通retry限制保持。

#### Scenario: 成功预检使用真实检查点形状

- **GIVEN** prepared workflow实际重用了原A的MR，或确认target包含原A
- **WHEN** 原结果事务接收MR_REUSED或ALREADY_MERGED
- **THEN** MR_REUSED必须带原I/source/target/A及有效IID/URL，ALREADY_MERGED允许无IID/URL
- **AND** 无需为补字段额外查询或写入GitLab，实际CREATE_MR POST为0，结果身份不得与原意图矛盾

#### Scenario: 失败与UNKNOWN接受既有最小结果patch

- **GIVEN** 完整意图确未SEND，实际结果为FAILED、MERGE_BLOCKED或UNKNOWN
- **WHEN** 原结果消费者提交真实结果patch
- **THEN** 保留既有失败/阻塞分类，UNKNOWN可只含blockReason等现有最小字段，不强制成功MR专属字段
- **AND** 已提供项目、source/target、SHA或MR身份不得矛盾；CREATED或非空git.requestId拒绝NOT_SENT

#### Scenario: NOT_SENT与结果及匹配guard一起提交

- **GIVEN** 当前fence与完整noSEND证明在原结果事务中再次核准
- **WHEN** 写入实际本地结果
- **THEN** NOT_SENT本地事件、Task/Step/metadata、队列/Run与匹配guard释放同事务提交
- **AND** ENSURE_MR仅保持前序binding事实，不新增binding写入，不声称本动作验证了binding写入回滚
- **AND** Task UNKNOWN继续NEEDS_REVIEW/后继阻断及普通retry限制，不因reservation释放获得自动重放权

#### Scenario: 竞争或结果回滚不留下半条收口

- **GIVEN** 新旧owner、sending与NOT_SENT竞争，或结果事务已执行实际本地变更
- **WHEN** 当前fence失效、真实Task CAS=0或外层结果事务回滚
- **THEN** 发送与本地收口不能双赢；本次未提交NOT_SENT/Task/Step/metadata/队列/Run/guard变化整体回滚，零非法POST
- **AND** 此前REQUIRES_NEW已提交的INTENT/SEND/响应事实保持，不能把前置拒绝冒充经过写入阶段的回滚

#### Scenario: SEND尚无响应时不得提前本地UNKNOWN

- **GIVEN** 原SEND_STARTED已提交而同attempt尚无seq2
- **WHEN** 当前或迟到worker试图按本地UNKNOWN完成Task
- **THEN** 拒绝提前完成，保留真实finish入口，不形成NOT_SENT或抹去在途事实
- **AND** 已有真实seq2 UNKNOWN仍保留guard，不自动重发、不转换为NOT_SENT

#### Scenario: B8的两条阶段负控按B9资格迁移

- **GIVEN** B8阶段曾拒绝完整INTENT自动接管，并在原owner显式UNKNOWN时保留INTENT/guard
- **WHEN** B9完整资格与当前fence成立
- **THEN** 合法到期意图成为安全接管正例，原owner的真实noSEND UNKNOWN成为NOT_SENT原子收口
- **AND** 损坏、旧摘要、已SEND或UNKNOWN远端事实的禁区保持；本地NOT_SENT的真实HTTP计数为0，历史B8证据不改写

### Requirement: 运行步骤按已有任务事实呈现顺序

系统 SHALL 在原Run持久化映射职责内，复用Step.taskId和RunTask.taskOrder输出步骤顺序；核准归属的关联步骤在前，无关联或无可信关联的步骤保留在后并按已有时间字段退化。既有列表、详情与结果返回共享该顺序，不新增实体、字段、schema、DTO或API，不把数据库集合行序当执行事实。

#### Scenario: 关联步骤以真实任务顺序为准

- **GIVEN** Step.taskId可解析到同run、targetType=RUN_ITEM、同targetId且taskType匹配actionType的现Task
- **WHEN** 原Run映射生成steps列表
- **THEN** 关联步骤按taskOrder排列，即使数据库集合顺序或时间戳顺序与之相反
- **AND** BlockBoard取最后step、RunDrawer/RunDetail按列表展示以及原结果返回均消费同一顺序，不靠修改测试或前端另行排序掩盖错误

#### Scenario: 不借用外来任务的排序权

- **GIVEN** 关联Task缺失，或run/targetType/targetId/actionType任一不符
- **WHEN** 判断该Step的顺序依据
- **THEN** 不借用该Task.taskOrder，也不删除原Step；作为无可信关联的步骤保留
- **AND** 复用已有taskId/taskOrder；run_step没有id，不新增假定存在的自增id映射

#### Scenario: 混合步骤排序保持传递性与历史边界

- **GIVEN** 同一Item混有核准关联步骤和无关联步骤，时间戳可能交错或相同
- **WHEN** 形成最终顺序
- **THEN** 先将核准关联步骤按taskOrder排列，再将其余步骤按startAt/endAt退化，使用完整分组规则
- **AND** 不使用“双方有关联则比taskOrder、否则比时间”的非传递比较器；无Task且同时间戳时不声称恢复了真实先后

#### Scenario: 批量读取复用现有任务来源

- **GIVEN** 一批Run的steps需要映射，或completeTask已加载ordered任务列表
- **WHEN** 收集关联taskId并构造当前调用的局部Map
- **THEN** 复用现JpaRepository.findAllById至多一次读取整批关联任务，无关联id时零额外query；completeTask直接复用已有任务列表
- **AND** 不逐Run/Item/Step查询，不引入缓存、第二套存储、DTO字段或新接口

B10最终验收状态（不替代下列规范）：冻结d51a0b7生产的独立27例为23 FAIL/4正控，随后独立GREEN27/27；新增adapter24仅3固定+15响应+6 locator，旧18另回归，namespace/通用错误保留的源码边界不扩称新增覆盖。三条真实HTTP未加same-key零额外GET断言，幂等沿现回归与源码核验。UI基线4/4、完整前端218/218含coverage通过；单次相关1123/1123、API48/48、普通942（937 PASS/5旧SKIP）、静态与最终源码审计均通过，实现 `8dcca5bf8265a3a1f36c16a14f0e3b8b0b35652d` 已推送，固定SHA Full-Link44982102777 attempt 1已completed/success，5 PASS/0 FAIL，B10已DONE；证据见记录23.7–23.11。B11读取状态证明已DONE，独立契约与本地验收见记录24，实现35ca21366ec9124fe9c4d6d2ef162a09c13709fb已推送，固定SHA Full-Link44984865795 attempt 1为5 PASS/0 FAIL；当前唯一HEAD为B12「无自身写意图任务的失败与重试闭环」；本CI不扩称新观察的真实远端或完整UI旅程。

### Requirement: MERGE_MR精确只读观察使用完整远端证明

系统 SHALL 仅按原项目I、正整数MR IID和合法期望SHA A只读核对MERGE_MR，并复用confirmedMergedMr对实际GET状态与原始正文的完整B4证明。FOUND只表示该对象当前merged，不证明原PUT成功或原业务计划满足；证据保留十字段的原JSON scalar/null类型，不新增接口或完整请求hash门禁。

#### Scenario: 原三字段与当前五字段请求使用同一精确定位

- **GIVEN** 原MERGE_MR target为历史三字段或当前五字段，已通过现仓库/provider/base和项目身份核对
- **WHEN** 读取typed method、suffix和sha
- **THEN** method必须为文本PUT，suffix完整匹配`/merge_requests/[1-9][0-9]*/merge`，A必须为文本且满足既有40/64位hex validSha
- **AND** 两种target均用同一精确GET与完整B4响应证明，不补全旧正文、不追加完整请求hash门禁或兼容路径

#### Scenario: 非法定位不能产生MR读取

- **GIVEN** method/suffix/IID/SHA缺失、类型错误或格式非法，包括零IID、非正整数、额外路径或query
- **WHEN** 核对MERGE_MR locator
- **THEN** 返回INCOMPLETE且MR GET=0，不猜测或修补locator
- **AND** 该计数不把原namespace身份GET算成业务MR GET；namespace由I指向J时不得向J发送业务GET

#### Scenario: 实际HTTP200与完整原始对象才形成FOUND

- **GIVEN** 精确MR GET返回object，实际HTTP状态为200
- **WHEN** 调用confirmedMergedMr(actualStatus,raw,I,IID,A)
- **THEN** 原始project_id/source_project_id/target_project_id必须均为匹配I的正整数，iid为匹配IID的正整数，sha合法且与A语义相等，state为merged
- **AND** source_branch、target_branch、web_url及现检查点完整性成立，merge_commit_sha字段存在且为null或合法SHA文本，才返回FOUND

#### Scenario: 不完整正文与不满足证明的对象分别分类

- **GIVEN** 实际读取返回absent、JSON null、非object，或返回HTTP200 object但证明不完整
- **WHEN** 形成观察结果
- **THEN** absent/null/非object为INCOMPLETE；HTTP200 object包括空对象在不满足完整证明时为MISMATCH
- **AND** HTTP201即使正文完整仍为MISMATCH，不伪造actualStatus=200；403/404/不可用沿既有FORBIDDEN/NOT_FOUND/UNAVAILABLE分类

#### Scenario: 保留实际分支而不推定原写入因果

- **GIVEN** I/IID/A对象当前已merged，但实际source或target不同于原业务计划，或由其他操作者完成
- **WHEN** 完整对象证明成立
- **THEN** FOUND仅描述当前对象事实，实际source_branch/target_branch照实保存，不据此确认原PUT成功或原计划target满足
- **AND** 不解析merge_commit_message推导计划，不扩Operation/Task关联接口，也不将观察等同原Task结果

#### Scenario: 十字段证据保留原JSON类型

- **GIVEN** 原始MR对象含合法字段、错误scalar类型、null或嵌套内容
- **WHEN** 生成evidenceJson
- **THEN** 只摘取project_id/source_project_id/target_project_id/iid/source_branch/target_branch/sha/web_url/state/merge_commit_sha中实际存在的scalar/null值，保留类型及值
- **AND** 不将数字或布尔值转为文本，不为缺失字段补null；object/array省略，token/header/无关正文不记录，既有API/UI直接消费该证据

### Requirement: 只读观察与真实写入结果独立提交

系统 SHALL 复用现reconcile和Journal.observe记录观察，只新增Observation及operation.version/updatedAt，不改变Task/Step/Run/metadata/guard/events/attempt或原执行状态。观察版本约束观察提交，不剥夺原seq1 sender按既有资格finish的权力；UNKNOWN后FOUND仍不得完成任务、释放guard或重发。

#### Scenario: UNKNOWN后FOUND仍保留未知执行事实

- **GIVEN** 原MERGE_MR真实写结果为UNKNOWN，原Run为NEEDS_REVIEW且guard保留
- **WHEN** 现reconcile只读获得完整FOUND并提交
- **THEN** 仅新增Observation和operation.version/updatedAt，UNKNOWN/NEEDS_REVIEW及Task/Step/metadata/guard/events/attempt保持
- **AND** 原UI展示观察与未知提示，不产生seq2、后继执行或重发

#### Scenario: 观察先提交仍允许原sender真实完成

- **GIVEN** 原PUT已由当前合法seq1 sender发送，响应尚未返回，观察GET已完成
- **WHEN** observe先提交并增加operation.version，随后原PUT返回真实响应
- **THEN** 原sender仍按现fence/状态/seq1资格finish，不因观察version变化被拒绝
- **AND** seq2来自真实响应，观察不替代写入结果，不引入expectedVersion发送门禁

#### Scenario: 真实finish先提交时旧观察零写

- **GIVEN** reconcile已取得旧version并开始GET
- **WHEN** 原PUT响应先由finish提交，旧GET随后调用observe
- **THEN** expectedVersion已过期，观察返回STALE且不新增Observation或改写任何执行事实
- **AND** 原真实finish及seq2保留，不能把该竞争改成重发或清guard

#### Scenario: 复用原入口与幂等观察消费者

- **GIVEN** 现GitOperationController、GitOperationAppService和GitOperationsPanel消费Observation
- **WHEN** 相同actor/key和原expectedVersion再次请求核对
- **THEN** 沿现幂等路径返回已有观察，不新增并行接口或自动恢复动作
- **AND** GET位于账本事务外，提交时核版本；现UI只展示观察和原执行状态，不把FOUND扩展为恢复动作

### Requirement: GitLab MR读取须先证明实际GET成功状态

系统 SHALL 在GitLabMergeRequestAdapter既有getMap/readPages出口先核实际GET状态，仅200可继续正文与分页证明。未抛异常而返回非200须为INCOMPLETE/null；既有异常分类保持。分页任一后页失败须丢弃累计匹配并停止后续读取，readSource/read/find/contains不得以部分证据确认存在、包含或完整缺席。

#### Scenario: 完整200仍须通过原正文证明

- **GIVEN** readSource/read/find/contains使用既有GET出口
- **WHEN** 实际响应为200
- **THEN** 继续原正文、身份、SHA、URL与分页完整性校验；合法完整证据保留原结果，空正文等既有反例仍拒绝
- **AND** 不因新增状态检查放宽任何原证明

#### Scenario: 非200正文不能成为成功证据

- **GIVEN** GET未抛异常而实际返回201、206或其他非200状态
- **WHEN** 正文看似完整匹配、空列表或带结束分页头
- **THEN** 在消费正文和分页头前返回INCOMPLETE/null，不产生FOUND或完整缺席结论

#### Scenario: 后页失败丢弃前页匹配

- **GIVEN** 首个200页已有MR或target匹配，仍指向下一页
- **WHEN** 后页非200正常返回，或抛出现有读取异常
- **THEN** 丢弃累计匹配且无部分结果；正常返回非200为INCOMPLETE/null，抛异常沿现分类
- **AND** 不再请求随后页，不因已有匹配提前成功

#### Scenario: 异常分类与200分页正控保持

- **GIVEN** 现GET抛出401/403、404或网络等异常，或各页均为200且满足原完整分页规则
- **WHEN** 两个既有出口处理结果
- **THEN** 异常仍按原FORBIDDEN、NOT_FOUND、UNAVAILABLE返回；完整200分页仍可按原正文规则产生FOUND
- **AND** 不新增重试、缓存、接口或target快照

#### Scenario: 实际编排消费者拒绝不具资格的读取

- **GIVEN** fresh ENSURE_MR、prepared ENSURE_MR或TRY_MERGE沿现workflow消费上述结果
- **WHEN** 分页、包含关系或MR详情只取得非200正常返回的正文
- **THEN** 沿既有未知/失败分类和结果事务收口，不据此确认ALREADY_MERGED、创建MR或发送merge PUT
- **AND** 不扩大原SEND/UNKNOWN恢复权，不改变POST201与merge PUT的既有写响应资格

B12当前准备状态（详细规范后续定义）：当前Run/Task锁与token核准下，首次结果提交前无任何本Task operation的UNKNOWN拟按FAILED收口，保留原message/metadata，由现新Run retry直接消费。有任何operation或历史UNKNOWN不在本片，不以FOUND直接恢复；拟由原ack返回现RunItemResult供唯一completeTask消费者使用，不新增DTO或Port方法。独立HTTP/PG测试仍仅内存准备，代码未落盘、测试未运行，详细合同后续补齐。

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
