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
