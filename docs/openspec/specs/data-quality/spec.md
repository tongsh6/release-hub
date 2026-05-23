# data-quality Specification

## Purpose

存量数据质量治理用于在验收或发布操作前识别历史数据风险，并把风险转成可审计、可人工复核的清理动作清单。该能力不绕过业务不变量，不直接伪造状态，不输出敏感明文。

## Requirements

### Requirement: 存量数据安全清理 dry-run

系统 SHALL 提供独立的存量数据安全清理 dry-run 入口，输出资产统计、风险清单和建议动作。

#### Scenario: 生成 dry-run 清理报告

- **WHEN** 操作者运行 SA-002 存量数据安全清理脚本
- **THEN** 系统生成 `summary.md`、`actions.md` 和 `actions.jsonl`
- **AND** 每条动作包含资源类型、资源 ID、风险类型、建议动作、是否已执行、应用入口、执行前检查、执行后复核和人工复核决策
- **AND** 默认模式不修改数据库、不删除发布窗口、不触碰 GitLab 远端分支或仓库
- **AND** 报告不得输出 token 明文

#### Scenario: 拒绝自动执行危险清理

- **WHEN** 操作者尝试使用自动执行模式批量清理存量数据
- **THEN** 系统拒绝执行，并提示使用 dry-run 动作清单和应用层入口完成人工复核后的修复

### Requirement: 存量清理动作人工复核入口

系统 SHALL 提供应用层人工复核入口，用于接收 dry-run 动作清单并判断动作是否可以进入对应应用页面或受控迁移服务。

#### Scenario: 人工复核通过但不执行清理

- **WHEN** 操作者提交 reviewer、来源报告和已人工确认的清理动作
- **THEN** 系统返回动作复核结果
- **AND** 支持的资源/风险组合可标记为 `ACCEPTED`
- **AND** 响应必须返回应用入口、执行前检查和执行后复核口径
- **AND** 该入口不得直接修改数据库、删除发布窗口或触碰 GitLab 远端资源

#### Scenario: 拒绝越权或不完整动作

- **WHEN** 动作要求 `EXECUTE_DIRECTLY`、`AUTO_EXECUTE` 或缺少应用入口、执行前检查、执行后复核口径
- **THEN** 系统将该动作标记为 `REJECTED`
- **AND** 响应说明拒绝原因
- **AND** 动作不会进入执行状态

### Requirement: 存量数据风险类型

系统 SHALL 至少识别仓库 token 明文、系统设置 token 明文、BranchCreationMode 缺失或非法、featureBranch 缺失、cloneUrl 异常、DRAFT 发布窗口残留和挂载分支未创建。

#### Scenario: 识别风险并给出业务修复入口

- **WHEN** 存量数据存在上述风险
- **THEN** dry-run 报告为每个资源输出对应风险类型
- **AND** 建议动作指向仓库设置、系统设置、迭代详情、发布窗口页面或受控迁移服务
- **AND** 不建议直接通过数据库 UPDATE/DELETE 绕过应用层约束
