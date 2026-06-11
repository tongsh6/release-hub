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

### Requirement: 版本更新前置版本状态可解释

系统 SHALL 在版本更新入口依赖仓库初始版本时，复用仓库版本解析诊断作为用户可见前置状态。

#### Scenario: 异常仓库不会表现为不明失败

- **WHEN** 仓库默认分支为空、缺少版本文件、版本文件缺少版本声明或版本值格式异常
- **THEN** 仓库初始版本接口返回明确的 `VERSION_FILE_MISSING`、`VERSION_DECL_MISSING`、`VERSION_INVALID` 或 `VERSION_READ_ERROR`
- **AND** 响应包含仓库默认分支、检查路径和说明文案
- **AND** 前端仓库详情/抽屉展示这些诊断，供版本更新前修复
