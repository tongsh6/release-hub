# Change: 增加分支推导服务（版本校验链路）

## Why

当前版本校验接口仅推导 `derivedVersion`，`derivedBranch` 字段长期为空。发布流程中分支命名已约定为 `release/<windowKey>`，但缺少统一推导入口和规则一致性校验，导致前端无法在执行前拿到可靠目标分支。

## What Changes

- 在 `VersionValidationAppService` 中新增分支推导能力：基于 ReleaseWindow 和 BranchRule 推导 `derivedBranch`
- 优先使用 ALLOW 规则模式推导候选分支，并执行 BranchRule 一致性校验
- 保持现有版本推导行为不变，仅扩展返回结果中的 `derivedBranch`
- 增加应用层与 API 集成测试，覆盖推导成功和规则不匹配场景

## Impact

- 受影响 specs：`specs/version-update/spec.md`（新增 delta）
- 受影响代码：
  - `release-hub/releasehub-application/src/main/java/io/releasehub/application/version/VersionValidationAppService.java`
  - `release-hub/releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/VersionUpdateController.java`
  - `release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/api/VersionValidationApiTest.java`

## 需求文档

- [版本更新功能增强](../../../requirements/in-progress/版本更新功能增强.md)
