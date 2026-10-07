## ADDED Requirements

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

### Requirement: 历史记录与补偿边界

系统 SHALL 保留旧 Run 的查询导出，不自动构造历史操作意图或重放；补偿必须是有来源、前置条件和明确授权的新计划。

#### Scenario: 打开历史 Run
- **WHEN** 用户查看无新协议元数据的历史 Run
- **THEN** 按 legacy 口径展示记录
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

系统 SHALL 在新协议编排触发接口的受理事务提交后返回 202 与 Run ID，由数据库队列和有界 worker 执行；窗口状态与自动编排受理意图在同一事务中持久化。发布窗口接口返回业务状态，不等待长任务完成。

#### Scenario: 异步任务不阻塞受理
- **WHEN** 用户发布窗口或触发编排
- **THEN** 系统先提交持久受理意图并返回相应业务响应
- **AND** 后台任务独立执行，用户能用 Run 查询进度

#### Scenario: 发布后回调丢失
- **GIVEN** 窗口状态和编排意图已一起提交
- **WHEN** AFTER_COMMIT 唤醒没有执行
- **THEN** 数据库轮询仍能发现并处理意图
- **AND** 不创建重复计划
