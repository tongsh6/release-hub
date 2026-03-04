## ADDED Requirements

### Requirement: 版本校验返回分支推导结果

系统 SHALL 在版本校验时根据 ReleaseWindow 与 BranchRule 推导目标分支，并在响应中返回 `derivedBranch`。

#### Scenario: 使用 ReleaseWindow 推导 release 分支

- **WHEN** 用户调用 `POST /api/v1/release-windows/{id}/validate` 且提供合法 `policyId` 与 `currentVersion`
- **THEN** 系统返回 `valid=true`
- **AND** 返回 `derivedVersion`
- **AND** 返回符合 BranchRule 的 `derivedBranch`

#### Scenario: 推导分支不符合规则

- **WHEN** 用户调用版本校验接口且推导出的分支不满足当前 BranchRule
- **THEN** 系统返回 `valid=false`
- **AND** 返回明确的 `errorMessage`
- **AND** 不影响已有 `derivedVersion` 推导逻辑的兼容性
