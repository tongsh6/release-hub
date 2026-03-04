## ADDED Requirements

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
