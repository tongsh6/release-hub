## ADDED Requirements

### Requirement: 历史 BranchCreationMode 受控补齐

系统 SHALL 保护迭代仓库关联中的 `branch_creation_mode` 语义，历史缺失或非法值只能通过受控迁移服务补齐，不得通过通用数据质量清理绕过迭代领域边界。

#### Scenario: 只补齐分支创建模式字段

- **WHEN** BranchCreationMode 迁移服务处理历史 `iteration_repo` 记录
- **THEN** 系统 SHALL 只允许更新 `branch_creation_mode`
- **AND** 系统 SHALL 保持 `feature_branch`、版本字段、迭代仓库集合和发布窗口状态不变
- **AND** 系统 SHALL 不创建、删除、重命名或查询 GitLab 远端分支作为默认迁移动作

#### Scenario: 无法推断真实模式时要求人工映射

- **WHEN** 历史记录的 feature 分支无法仅凭本地数据区分 `NAMED` 与 `EXISTING`
- **THEN** 系统 SHALL 将该记录标记为需要人工映射
- **AND** 系统 SHALL 禁止自动写入 `NAMED` 或 `EXISTING`
- **AND** 人工映射 SHALL 作为迁移批次审计证据保存

#### Scenario: 迁移后迭代详情保持可读

- **WHEN** 迁移批次完成
- **THEN** 迭代详情、迭代仓库分页和 version-info SHALL 返回可解析的 `branchCreationMode`
- **AND** 返回值 SHALL 只能是 `AUTO`、`NAMED` 或 `EXISTING`
- **AND** 若复核发现不可解析值，迁移批次 SHALL 记录失败并输出回滚计划
