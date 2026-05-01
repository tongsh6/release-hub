# Change: 支持 Maven 多模块版本一致性同步

## Why

当前 Maven 版本更新仅处理单个 `pom.xml`，多模块场景下只更新父 POM，子模块 `parent.version` 与显式 `project.version` 可能不一致，导致构建与发布行为不稳定。

## What Changes

- 扩展 `MavenVersionUpdaterAdapter`：在多模块项目中同步更新父/子模块版本
- 更新策略：
  - 更新父 POM `project.version`
  - 更新子模块 `parent.version`（指向父模块时）
  - 子模块显式 `project.version` 若与旧父版本一致，则同步更新为新版本
- 保持单模块兼容行为不变
- 增加基础设施单元测试和 API 集成测试覆盖多模块场景

## Impact

- Affected specs: `version-update`
- Affected code:
  - `release-hub/releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/version/MavenVersionUpdaterAdapter.java`
  - `release-hub/releasehub-infrastructure/src/test/java/io/releasehub/infrastructure/version/MavenVersionUpdaterTest.java`
  - `release-hub/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/api/VersionUpdateApiTest.java`

## Requirement Link

- [版本更新功能增强](../../../requirements/in-progress/版本更新功能增强.md)
