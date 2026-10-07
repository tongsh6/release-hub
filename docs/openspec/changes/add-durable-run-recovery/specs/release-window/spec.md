## MODIFIED Requirements

### Requirement: 发布收尾编排与任务追踪

系统 SHALL 沿用当前已验收页面“关闭窗口触发收尾”的入口，在同一数据库事务中保存业务 CLOSED 状态和持久收尾意图，再异步执行收尾任务。业务关闭和收尾执行状态分别展示，不再用“关闭前已完成收尾”的旧描述推断当前行为。

#### Scenario: 关闭后收尾尚未执行
- **WHEN** 用户关闭窗口且关闭事务提交
- **THEN** 窗口显示 CLOSED，收尾 Run 显示已受理
- **AND** 即使提交后的内存回调未运行，数据库队列仍能发现收尾意图

#### Scenario: 收尾步骤结果未知
- **GIVEN** 窗口已关闭且收尾步骤可能已产生外部副作用
- **WHEN** 系统未得到确定结果
- **THEN** 收尾任务进入 UNKNOWN 或 NEEDS_REVIEW
- **AND** 页面不宣称收尾成功，也不自动把窗口恢复为 PUBLISHED

### Requirement: 收尾任务类型

系统 SHALL 保留合并、tag、CI、分支归档和迭代归档的业务顺序，并将每个可能独立产生副作用的请求拆为持久任务。每项的前置条件和结果未知处理遵循新 Run 协议。

顺序为：MERGE_RELEASE_TO_MASTER → CREATE_TAG → TRIGGER_CI → ARCHIVE_BRANCHES → ARCHIVE_ITERATION。分支归档再拆为创建归档引用和删除源引用，CI 必须在所需 release 引用仍可访问时触发。

#### Scenario: 任务按顺序执行
- **GIVEN** 一个包含收尾任务的 Run
- **WHEN** 调度器执行计划
- **THEN** 仅在前置任务已确认完成后派发后继任务
- **AND** task_order 和依赖记录与实际执行一致

#### Scenario: 确认失败停止后继任务
- **GIVEN** 收尾任务确认失败且不再允许重试
- **WHEN** 调度器处理结果
- **THEN** 不继续派发依赖该任务的步骤
- **AND** Run 记录失败及未执行原因；若仍有未知副作用则优先进入恢复或待复核状态

#### Scenario: 归档引用已创建但源分支未删除
- **GIVEN** 创建归档引用任务已确认完成
- **WHEN** 删除源分支步骤未执行或结果未知
- **THEN** 两个步骤分别可查询
- **AND** 只有源 SHA、归档证据及并发边界满足条件时，才允许继续删除

#### Scenario: Pipeline 响应丢失
- **WHEN** 触发 pipeline 的请求可能已被接收但响应丢失
- **THEN** 系统按操作关联标识和外部证据核对
- **AND** 不能确认唯一结果时进入人工复核，不自动重复触发
