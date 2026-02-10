# run Specification

## Purpose
TBD - created by archiving change add-release-automation. Update Purpose after archive.
## Requirements
### Requirement: 运行任务管理

系统 SHALL 支持运行任务（RunTask）的创建、执行和状态管理。

#### Scenario: 创建运行任务

- **GIVEN** 一个 Run 记录
- **WHEN** 系统创建 RunTask
- **THEN** RunTask 包含任务类型、目标对象、执行顺序
- **AND** 初始状态为 PENDING

#### Scenario: 查看运行任务列表

- **GIVEN** 一个 Run 记录，包含多个 RunTask
- **WHEN** 用户调用 GET /api/v1/runs/{id}/tasks
- **THEN** 返回该 Run 下所有 RunTask 列表
- **AND** 按 task_order 排序

### Requirement: 任务状态流转

RunTask 状态 SHALL 按以下规则流转：

- PENDING → RUNNING（开始执行）
- RUNNING → COMPLETED（执行成功）
- RUNNING → FAILED（执行失败且重试次数达上限）
- FAILED → RUNNING（手动重试）

#### Scenario: 任务执行成功

- **GIVEN** 一个 PENDING 状态的 RunTask
- **WHEN** 任务开始执行
- **THEN** 状态变为 RUNNING，记录 started_at
- **WHEN** 任务执行成功
- **THEN** 状态变为 COMPLETED，记录 finished_at

#### Scenario: 任务执行失败重试

- **GIVEN** 一个 RUNNING 状态的 RunTask，max_retries 为 3
- **WHEN** 任务执行失败
- **THEN** retry_count 加 1，记录 error_message
- **WHEN** retry_count < max_retries
- **THEN** 自动重试执行
- **WHEN** retry_count >= max_retries
- **THEN** 状态变为 FAILED

### Requirement: 手动重试任务

系统 SHALL 支持对 FAILED 状态的任务进行手动重试。

#### Scenario: 手动重试失败任务

- **GIVEN** 一个 FAILED 状态的 RunTask
- **WHEN** 用户调用 POST /api/v1/runs/{id}/tasks/{taskId}/retry
- **THEN** 任务状态变为 RUNNING
- **AND** 系统重新执行该任务

#### Scenario: 非失败状态不可重试

- **GIVEN** 一个 COMPLETED 状态的 RunTask
- **WHEN** 用户调用重试 API
- **THEN** 返回错误"只有失败状态的任务可以重试"

### Requirement: 异步任务执行

系统 SHALL 异步执行运行任务，不阻塞主流程。

#### Scenario: 异步执行不阻塞

- **GIVEN** 发布窗口发布操作
- **WHEN** 系统创建 Run 和 RunTask
- **THEN** 发布 API 立即返回成功
- **AND** 任务在后台异步执行

