# version-update Specification

## Purpose

定义发布窗口版本校验与版本更新的核心行为，确保版本推导和分支推导在执行前可被一致验证。

## Requirements

### Requirement: 版本校验返回版本与分支推导结果

系统 SHALL 在版本校验时根据 VersionPolicy 推导目标版本，并根据 ReleaseWindow 与 BranchRule 推导目标分支。

#### Scenario: 版本与分支推导成功

- **WHEN** 用户调用 `POST /api/v1/release-windows/{id}/validate` 且提供合法 `policyId` 与 `currentVersion`
- **THEN** 系统返回 `valid=true`
- **AND** 返回 `derivedVersion`
- **AND** 返回 `derivedBranch`

### Requirement: 分支推导结果必须满足 BranchRule

系统 SHALL 校验推导出的分支名称是否满足当前 BranchRule。

#### Scenario: 推导分支不合规

- **WHEN** 系统推导出的分支不满足 BranchRule
- **THEN** 系统返回 `valid=false`
- **AND** 返回明确的 `errorMessage`

### Requirement: Maven 多模块版本一致性同步

系统 SHALL 在执行 Maven 版本更新时，保证父模块与子模块版本引用的一致性。

#### Scenario: 同步父模块与子模块 parent 版本

- **WHEN** 用户对多模块 Maven 项目执行版本更新并提供目标版本号
- **THEN** 系统更新父 POM `project.version`
- **AND** 更新子模块中指向父模块的 `parent.version`

#### Scenario: 同步显式子模块版本

- **WHEN** 子模块存在显式 `project.version` 且该值与旧父版本一致
- **THEN** 系统将该子模块 `project.version` 同步为目标版本
- **AND** 不修改与旧父版本不一致的显式子模块版本
