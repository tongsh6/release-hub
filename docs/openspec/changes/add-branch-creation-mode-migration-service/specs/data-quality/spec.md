## ADDED Requirements

### Requirement: BranchCreationMode 风险进入独立迁移服务

系统 SHALL 将 `BRANCH_CREATION_MODE_MISSING_OR_INVALID` 保持为 `MIGRATION_REQUIRED` 风险，并要求通过独立迁移服务完成 dry-run、人工确认、受控执行、复核和回滚审计。

#### Scenario: 复核队列不得直接执行 BranchCreationMode 迁移

- **WHEN** SA-002 dry-run 或复核队列识别到 `BRANCH_CREATION_MODE_MISSING_OR_INVALID`
- **THEN** 复核结果 SHALL 标记为 `MIGRATION_REQUIRED`
- **AND** 数据质量复核队列和处置 case SHALL 只展示独立迁移服务 proposal、批次或报告入口
- **AND** 系统 SHALL 禁止从复核队列、处置 case 或通用清理脚本直接更新 `iteration_repo.branch_creation_mode`

#### Scenario: 迁移服务先生成 dry-run 候选

- **WHEN** 操作者启动 BranchCreationMode 迁移服务 dry-run
- **THEN** 系统 SHALL 只读扫描历史 `iteration_repo.branch_creation_mode` 缺失、空白、可规范化或非法值
- **AND** dry-run SHALL 输出候选分类、推断依据、建议动作、是否需要人工映射、执行前检查和拒绝原因
- **AND** dry-run SHALL 不修改数据库、不修改业务字段、不触碰 GitLab 远端资源

#### Scenario: 无人工确认不得执行迁移

- **WHEN** 迁移批次包含候选动作
- **THEN** 系统 SHALL 要求操作者确认执行计划
- **AND** `MANUAL_MAPPING_REQUIRED` 候选 SHALL 在人工确认目标枚举前保持不可执行
- **AND** `NOT_MIGRATABLE_IN_THIS_SERVICE` 候选 SHALL 输出原因并保持拒绝状态

#### Scenario: 迁移结果必须可复核和回滚

- **WHEN** 迁移服务受控执行已批准批次
- **THEN** 系统 SHALL 记录迁移前快照、候选级执行结果、迁移后复核和回滚清单
- **AND** 执行后 SHALL 通过 API/version-info 或数据库只读审计证明 `branchCreationMode` 可被领域枚举解析
- **AND** 回滚 SHALL 只恢复本批次写入前的 `branch_creation_mode` 原值，不得覆盖后续合法变更
