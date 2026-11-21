# 窗口配置与收尾规范增量

## ADDED Requirements

### Requirement: 配置与执行分离

范围说明：Attach/Detach 与 Publish 的 MANUAL 边界属于 S3-A/B1；仓库级收尾持久受理属于 S3-B2。C1 固定四动作任务只用于窗口集成，后台异步及收尾恢复仍是 C2/S4 目标。CLOSED 只表达业务生命周期，不能当作收尾成功。

系统 SHALL 将 Attach/Detach 作为关系维护，将 Publish 作为生命周期变化；默认不写 GitLab、不创建执行 Run。Orchestrate 是 feature → release 的唯一显式入口。

#### Scenario: 批量挂载包含无效迭代
- **WHEN** 批量 Attach 的任一引用不满足存在性、分组或生命周期约束
- **THEN** 整批零绑定写入、零 Git 写入
- **AND** 有效重复 Attach 不改变原 attachAt

#### Scenario: 解除共享迭代
- **WHEN** 用户 Detach 一个仍被其他窗口使用的迭代
- **THEN** 只删除当前窗口关系
- **AND** 不回滚已合并内容、不关闭全局 Iteration、不归档或删除 feature/release

## MODIFIED Requirements

### Requirement: 发布收尾编排与任务追踪

系统 SHALL 使用现有关闭入口和 Run/计划模型，在短事务中原子受理窗口 CLOSED 状态与仓库级收尾计划，再在该事务之外执行远端动作；异步 worker 属于 S4。业务关闭和收尾执行状态分别展示。重复或并发 close 不得派发第二个收尾批次。

#### Scenario: 触发收尾编排
- **WHEN** 用户关闭窗口且关闭事务提交
- **THEN** 窗口显示 CLOSED，收尾 Run 显示已受理
- **AND** 即使执行尚未开始或进程退出，原 Run 与封存计划仍可查询；自动接续须待恢复能力验收

#### Scenario: 收尾步骤结果未知
- **GIVEN** 窗口已关闭且收尾步骤可能已产生外部副作用
- **WHEN** 系统未得到确定结果
- **THEN** 收尾 Item/Step 记录 UNKNOWN，Run 进入 NEEDS_REVIEW
- **AND** 页面不宣称收尾成功，也不自动把窗口恢复为 PUBLISHED

### Requirement: 收尾任务类型

系统 SHALL 以 `(window, repository)` 为收尾执行单位，每仓只执行一次封存的合并、Tag 与明确选择的 CI 序列。参与迭代集合、目标分支、Tag 名称及目标 SHA 必须有确定来源。版本冲突在受理前拒绝，不选择任一迭代冒充仓库所有者。

依赖顺序为合并 → 精确 SHA 的 Tag → 已选择的 CI；任一步失败或未知停止后继步骤。缺失 release 不得回退默认分支继续发布。关闭一个窗口不改变共享 Iteration 的全局状态，也不自动归档/删除 feature 或 release。尚无原子 SHA 删除前置条件时保持 DELETE 阻断。

#### Scenario: 任务按顺序执行
- **GIVEN** 一个包含收尾步骤的 Run，且尚未接入 C1 的窗口集成任务协议
- **WHEN** 首次受理者执行封存计划
- **THEN** 仅在前置步骤已确认完成后派发后继步骤
- **AND** plannedOrder、executedOrder 和步骤记录与实际执行一致

#### Scenario: 任务执行失败停止
- **GIVEN** 收尾任务确认失败且不再允许重试
- **WHEN** 执行器处理结果
- **THEN** 不继续派发依赖该任务的步骤
- **AND** Run 记录失败及未执行原因；若仍有未知副作用则优先进入恢复或待复核状态

#### Scenario: 共享迭代与分支仍被使用
- **GIVEN** 同一 Iteration/Repository 被另一个窗口引用
- **WHEN** 当前窗口关闭并执行收尾
- **THEN** 保留全局 Iteration 状态和原 feature/release 分支
- **AND** 所有 DELETE 请求为零，不把保留描述为归档成功

#### Scenario: 同名 Tag 已存在
- **WHEN** 精确读取同名 Tag
- **THEN** 同 SHA 复用且不发送 POST；不同 SHA 阻断后续 CI
- **AND** 读取失败、不可达或身份不明不能作为“不存在”证据

#### Scenario: Pipeline 响应丢失
- **WHEN** 触发 pipeline 的请求可能已被接收但响应丢失
- **THEN** 系统按操作关联标识和外部证据核对
- **AND** 不能确认唯一结果时进入人工复核，不自动重复触发

#### Scenario: 重复与并发关闭
- **WHEN** 多个操作者以同一 CI 选择关闭同一窗口
- **THEN** 同一窗口行锁内只受理一个 Run/计划，后续请求返回原 Run 和原操作者
- **AND** 首个 Git 调用前 CLOSED/Run/计划已经提交；第二请求不等待远端步骤完成
- **AND** 改变 CI 选择返回冲突；CLOSED 缺完整计划拒绝重新派发

#### Scenario: CI 默认关闭与精确证据
- **WHEN** close 请求省略 triggerCi 或明确 false
- **THEN** TRIGGER_CI 为 SKIPPED，没有 pipeline POST
- **WHEN** 用户明确选择 true
- **THEN** 受理要求明确稳定 Tag，写响应必须匹配 project/id/ref/SHA/url
- **AND** CI_TRIGGERED 不代表流水线执行通过，UNKNOWN 不解释成未配置 CI

#### Scenario: 只读恢复不能误认相似结果
- **WHEN** 已知 pipeline ID 的远端响应只有相同 ref、SHA 或项目却不匹配
- **THEN** 不得判定 FOUND，也不得解除未知写入保护
- **AND** Tag 名称和 SHA 均须精确匹配；缺少核对身份不通过列表搜索猜测

#### Scenario: 在途编排与关闭互斥
- **GIVEN** 窗口已有受理并持久化的 RUNNING 或 NEEDS_REVIEW 编排
- **WHEN** 用户首次关闭窗口
- **THEN** 在同一窗口锁内拒绝关闭，不创建收尾 Run、不写 Git
- **AND** PLANNED 预览不阻断；若关闭已先完成受理，后来的 Orchestrate/retry 必须拒绝
