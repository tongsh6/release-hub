## ADDED Requirements

### Requirement: 数据质量受控处置执行审计

系统 SHALL 在任何真实存量数据处置前创建受控处置执行审计 case，用于记录动作来源、执行前快照、人工处理状态、执行后复核、失败恢复和幂等防重。

#### Scenario: 从已接受复核动作创建处置 case

- **WHEN** 操作者基于 `ACCEPTED` 的 SA-002 dry-run 复核动作创建处置 case
- **THEN** 系统 SHALL 保存来源报告、复核批次、资源类型、资源 ID、风险类型、处置等级、应用入口、允许动作、回滚边界和审计记录口径
- **AND** 系统 SHALL 保存脱敏后的 dry-run 动作快照
- **AND** 系统 SHALL 生成稳定幂等键，重复创建同一动作时返回既有 case
- **AND** 创建 case 不得修改业务数据、关闭发布窗口、迁移业务字段或触碰 GitLab 远端资源

#### Scenario: 应用层人工处置只记录审计状态

- **WHEN** `APPLICATION_MANUAL` case 进入人工处置
- **THEN** 系统 SHALL 记录执行前检查结果和脱敏执行前快照
- **AND** 系统 SHALL 只提供目标应用入口跳转，由既有业务页面和应用服务完成真实动作
- **AND** 系统 SHALL 在操作者返回后记录执行后复核证据、复核人和复核时间
- **AND** 数据质量复核队列不得提供直接执行清理按钮

#### Scenario: 只读观察和迁移服务风险不允许直接执行

- **WHEN** case 的处置等级为 `OBSERVE_ONLY`、`MIGRATION_REQUIRED` 或暂缓
- **THEN** 系统 SHALL 记录阻断原因和下一步入口
- **AND** 系统 SHALL 禁止进入应用层执行状态
- **AND** `MIGRATION_REQUIRED` 风险 SHALL 要求独立迁移服务 proposal、迁移范围、回滚计划和验收证据

#### Scenario: 失败恢复和重复提交可审计

- **WHEN** 处置 case 执行前检查失败、人工处理失败或执行后复核失败
- **THEN** 系统 SHALL 记录失败阶段、失败原因、操作者和恢复说明
- **AND** 系统 SHALL 保留原始动作快照和执行前快照
- **AND** 重试 SHALL 显式关联原 case，不得覆盖原失败记录
- **AND** 失败记录不得暗示系统已自动恢复业务数据
