# Run 执行规范增量

当前实现范围为 S3-A/B1：同步 Orchestrate、持久受理与封存计划、统一状态、精确失败项重试、Git 操作账本及未决写阻断。以下涉及 RunTask、worker、自动核对后继续、202 响应和硬中断恢复的场景是 S3-C/S4 目标，未完成验收前不作为现有能力展示。

## ADDED Requirements

### Requirement: 唯一手动编排与权威计划

系统 SHALL 从窗口的绑定关系生成仓库并集和 `(window, iteration, repository)` 计划，完整校验后先保存 Run 与不可变快照，再执行 GitLab MR-first。计划包含真实 feature 分支模式、source/target、ref、提供方、仓库映射和稳定顺序；执行及重试不得重新解释这些目标。

#### Scenario: 相同仓库属于多个迭代
- **GIVEN** 同一仓库被窗口中的两个迭代引用
- **WHEN** 用户选择这两个迭代执行 Orchestrate
- **THEN** 二者按 attachAt 与稳定消歧顺序依次合并到同一 release 分支
- **AND** 计划次序与实际执行次序分别记录，共享仓库计数为一

#### Scenario: 所选范围存在无效绑定
- **WHEN** 仓库、迭代、分支模式或远端映射不完整或相互冲突
- **THEN** 整次受理被拒绝且没有 Git 写入
- **AND** 不以过滤无效项后的子集代替用户范围

### Requirement: 阻塞传播与结果聚合

系统 SHALL 默认 FAIL_FAST，仅显式选择时使用 CONTINUE_ON_BLOCK。失败或阻塞停止同仓后续项并记录 SKIPPED_DUE_TO_BLOCK；其他仓库可以继续。UNKNOWN 不受继续策略豁免，Run 必须进入 NEEDS_REVIEW。

#### Scenario: 第一个迭代阻塞
- **GIVEN** 同仓第二个迭代尚未执行且使用 FAIL_FAST
- **WHEN** 第一个迭代得到 MERGE_BLOCKED 或 FAILED
- **THEN** 第二个迭代记录跳过原因、前置 Item ID 和 executedOrder=0
- **AND** 不丢弃该项，也不把跳过记为成功

#### Scenario: 所有仓库或部分仓库失败
- **WHEN** Run 完成可确认的执行
- **THEN** 全仓完成为 DONE，全部仓库失败或没有有效执行为 FAILED，部分仓库失败为 PARTIAL_FAILED
- **AND** 任一未决副作用优先 NEEDS_REVIEW；API、分页筛选、详情和导出读取同一持久状态

### Requirement: 重试使用原计划与精确选择

系统 SHALL 只接受原 Run 的实际 Item ID，并为已确认失败或阻塞项创建有来源的新 Run/Item。成功、运行中、预览和 UNKNOWN 项不得普通重试；收尾项不得被转成 feature → release。

#### Scenario: 选择依赖阻塞而跳过的项
- **GIVEN** 原计划为 FAIL_FAST
- **WHEN** 用户只选择 SKIPPED_DUE_TO_BLOCK 而未选择其同仓失败前置项
- **THEN** 拒绝受理并解释依赖
- **AND** 不擅自扩大用户选择范围

#### Scenario: 当前分支配置已改变
- **WHEN** 用户重试一个具有有效原计划的失败项
- **THEN** 使用原快照的 source/target、分支模式、顺序和策略
- **AND** 当前映射或绑定不再满足前置条件时拒绝，不改写原计划

### Requirement: 确认身份与未决写保持一致

系统 SHALL 将 GitLab 写请求发送到已确认的数字项目 ID，并将正常返回的业务 UNKNOWN 纳入会话收口；传输层确认某请求 APPLIED 不能代替业务结果完整性。

#### Scenario: 传输成功但 MR 证据不完整
- **GIVEN** HTTP 2xx 已记录 APPLIED，但业务无法确认 MR 身份、分支或提交
- **WHEN** mergeBranch 返回 UNKNOWN
- **THEN** 已发生操作转入 NEEDS_REVIEW 并保留仓库 guard
- **AND** 后续新 Run 的写请求在远端发送前被拒绝

#### Scenario: namespace 在身份解析后被重用
- **GIVEN** namespace 读取对应项目 A，随后该路径可能映射项目 B
- **WHEN** 执行已受理的 Git 写请求
- **THEN** 最终 URI 固定为项目 A 的数字 ID，保留动作 suffix 与 query
- **AND** 不向可变 namespace 发送写请求

### Requirement: 外部写入前持久受理

系统 SHALL 在新执行协议的每次外部写入之前，持久化 Run、封存计划、任务和操作意图；协调器不得用覆盖整轮网络调用的数据库事务代替持久检查点。

#### Scenario: 受理提交后进程退出
- **GIVEN** 新协议 Run 已受理但没有派发外部写入
- **WHEN** 进程重启
- **THEN** Run 和任务仍可查询
- **AND** 调度器从持久状态继续，不依赖内存回调

#### Scenario: 计划预览不执行 Git 写入
- **WHEN** 用户创建 S1 持久计划预览
- **THEN** 系统返回可查询的计划
- **AND** 不派发任何 Git 写请求

### Requirement: 运行身份与请求幂等隔离

系统 SHALL 为新 Run、RunItem、Attempt 分配不碰撞的独立标识，使用单独的幂等键和规范化请求哈希去重；后续运行不得改变历史执行项归属。

#### Scenario: 同范围多次执行
- **WHEN** 同一仓库、迭代、窗口被纳入两个不同的新 Run
- **THEN** 两个 Run 的执行项独立保存
- **AND** 第一次运行的执行项和证据保持不变

#### Scenario: 同幂等键重复请求
- **WHEN** 同一调用主体以相同 key 和相同规范化请求再次提交
- **THEN** 返回原 Run
- **AND** 不新建或重复派发任务

#### Scenario: 幂等键对应不同请求
- **WHEN** 已使用的 key 对应的请求内容改变
- **THEN** 返回冲突
- **AND** 不修改原计划

### Requirement: 未知结果先核对

系统 SHALL 将可能已到达外部系统但未确认结果的写请求标为 UNKNOWN，并在独立核对后决定下一步，不得当作普通失败直接重试。

#### Scenario: Git 成功但结果保存失败
- **GIVEN** SEND_STARTED 已持久化，外部操作成功但结果未提交
- **WHEN** 系统恢复
- **THEN** 先核对精确操作标识和计划证据
- **AND** 确认成功时只补记结果，不重放外部写入

#### Scenario: 查询未找到结果但原请求可能在途
- **WHEN** 核对没有找到目标，但不能证明原请求已终结且未生效
- **THEN** 保持 UNKNOWN 或进入 NEEDS_REVIEW
- **AND** 不以一次不存在响应为依据再次发送写请求

### Requirement: 仓库写入互斥与过期执行者隔离

系统 SHALL 对同一规范化仓库建立原子写入保留，覆盖新旧内部入口；租约过期只允许接管核对，不能自动释放未决副作用的保留。

#### Scenario: 旧 worker 返回
- **GIVEN** 任务执行代次已经变化
- **WHEN** 旧 worker 尝试保存结果或继续派发
- **THEN** 数据库 CAS 拒绝旧结果覆盖
- **AND** 旧 worker 不得继续派发后续写请求

#### Scenario: 未决仓库被其他入口操作
- **GIVEN** 仓库仍有 UNKNOWN 的写操作
- **WHEN** 另一个 Run 或旧业务入口请求写该仓库
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

系统 SHALL 将归档引用创建和源引用删除作为独立操作；只有归档已确认且源引用满足删除前置条件时才允许发送删除，证据不足或外部并发保护能力未证实则保持阻断。

#### Scenario: 归档创建失败
- **WHEN** 归档引用创建失败或响应无法确认
- **THEN** 不发送源分支删除请求
- **AND** 保留失败或未知的独立证据

#### Scenario: 源引用在归档后漂移
- **WHEN** 删除前发现源引用已变化，或不能可靠保障删除前置条件
- **THEN** 阻断删除并要求复核
- **AND** 不把旧观察当作新的删除授权

### Requirement: 历史记录与补偿边界

系统 SHALL 保留旧 Run 的查询导出，不自动构造历史操作意图或重放；补偿必须是有来源、前置条件和明确授权的新计划。

#### Scenario: 打开历史 Run
- **WHEN** 用户查看无新协议元数据的历史 Run
- **THEN** 按实际已保存事实及单向规范化的统一执行状态展示记录
- **AND** 不提供无证据的自动恢复

#### Scenario: 合并已经发生
- **WHEN** 后续步骤失败
- **THEN** 不自动回滚合并、删除已有引用或重触发 CI
- **AND** 保留已发生事实和人工决策入口

## MODIFIED Requirements

### Requirement: 运行任务管理

系统 SHALL 为新协议提供真正持久化的 RunTask、依赖、尝试和证据查询；已有任务表或前端调用声明不构成能力已实现的证明。

#### Scenario: 创建运行任务
- **GIVEN** 一个已持久受理的新协议 Run
- **WHEN** 计划被封存
- **THEN** 创建包含任务类型、目标、顺序、依赖和前置条件的持久任务
- **AND** 初始状态为 PENDING，不得先执行后补记录

#### Scenario: 查看运行任务列表
- **GIVEN** 新协议 Run 已创建任务
- **WHEN** 用户调用 GET /api/v1/runs/{id}/tasks
- **THEN** 返回任务状态、顺序、依赖、阻断原因和尝试摘要
- **AND** 不返回 Token、请求头或敏感响应

### Requirement: 任务状态流转

系统 SHALL 明确区分 PENDING、RUNNING、COMPLETED、FAILED、UNKNOWN、NEEDS_REVIEW、SKIPPED 和 CANCELLED。UNKNOWN 只能经过证据核对进入可继续或终止状态。

#### Scenario: 任务执行成功
- **GIVEN** 一个 PENDING 任务
- **WHEN** worker 领取并按协议执行
- **THEN** 记录开始时间和尝试代次
- **AND** 只有外部证据和结果提交成功后，才记录 COMPLETED 与结束时间

#### Scenario: 已确认失败的自动重试预算
- **GIVEN** 计划允许有限自动重试，且请求已确认未生效、前置条件仍满足
- **WHEN** 当前尝试失败
- **THEN** 增加尝试计数并记录脱敏原因
- **AND** 未达到预算时回到 PENDING，预算耗尽或不允许重试时进入 FAILED
- **AND** UNKNOWN 不适用此自动重试规则

#### Scenario: 传输响应丢失
- **GIVEN** 写请求已进入 SEND_STARTED
- **WHEN** 连接中断或响应缺失
- **THEN** 任务进入 UNKNOWN
- **AND** 不能按普通 FAILED 的自动重试策略再次发送

### Requirement: 手动重试任务

系统 SHALL 只允许对已确认失败且满足安全重放条件的新协议任务创建有来源关联的新 Run 和 attempt；原终态 Run 不变，未知结果先核对，成功步骤不得重复执行。

#### Scenario: 非失败状态不可重试
- **GIVEN** 一个已完成或尚在运行的任务
- **WHEN** 用户请求 retry
- **THEN** 返回状态不允许重试的错误
- **AND** 不重复执行成功步骤

#### Scenario: 对 UNKNOWN 调用 retry
- **WHEN** 用户对 UNKNOWN 任务请求 retry
- **THEN** 返回需要核对的阻断原因
- **AND** 不派发外部写请求

#### Scenario: 已确认失败项重试
- **GIVEN** 任务已确认未生效且前置条件仍满足
- **WHEN** 用户显式重试
- **THEN** 创建有来源关联的新 Run 和 attempt，并返回新 Run ID
- **AND** 原始证据及已成功任务保持不变

### Requirement: 异步任务执行

系统 SHALL 在 S3-C 完成后，于显式 Orchestrate 的受理事务提交后返回 202 与 Run ID，由持久队列和有界 worker 执行。默认 MANUAL 下 Publish 仅返回业务状态，不生成编排意图；可选 AUTO_ON_ATTACH 必须另行实现显式开关并复用唯一受理入口。

#### Scenario: 异步任务不阻塞受理
- **WHEN** 用户显式触发 Orchestrate
- **THEN** 系统先提交持久受理意图并返回相应业务响应
- **AND** 后台任务独立执行，用户能用 Run 查询进度

#### Scenario: 受理后唤醒丢失
- **GIVEN** Run、封存计划和持久任务已一起提交
- **WHEN** AFTER_COMMIT 唤醒没有执行
- **THEN** 数据库轮询仍能发现并处理意图
- **AND** 不创建重复计划
