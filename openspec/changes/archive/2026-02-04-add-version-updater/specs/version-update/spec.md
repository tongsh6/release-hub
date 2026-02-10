## ADDED Requirements

### Requirement: 版本号自动更新
系统 SHALL 支持自动更新 Maven 和 Gradle 项目的版本号，生成更新前后的 diff，并记录执行结果。

#### Scenario: Maven 单模块版本更新
- **WHEN** 用户对 Maven 单模块项目执行版本更新，提供目标版本号
- **THEN** 系统解析 pom.xml，更新 `<project><version>` 标签为新版本，生成 diff，返回更新结果（成功/失败、diff、错误信息）

#### Scenario: Maven 多模块版本更新
- **WHEN** 用户对 Maven 多模块项目执行版本更新，提供目标版本号
- **THEN** 系统更新父 POM 的版本，子模块继承父版本（如果子模块有显式 version，提供策略：保持/统一/报错），生成 diff，返回更新结果

#### Scenario: Gradle properties 版本更新
- **WHEN** 用户对 Gradle 项目执行版本更新，项目使用 gradle.properties 定义版本
- **THEN** 系统更新 gradle.properties 中的 `version=` 属性为新版本，生成 diff，返回更新结果

#### Scenario: Gradle build.gradle 版本更新（不支持提示）
- **WHEN** 用户对 Gradle 项目执行版本更新，但版本定义在 build.gradle 中
- **THEN** 系统返回错误提示，说明当前不支持 build.gradle 版本更新，建议使用 gradle.properties

#### Scenario: 版本更新执行记录
- **WHEN** 用户执行版本更新
- **THEN** 系统创建 Run 记录（runType=VERSION_UPDATE），记录执行步骤（RunStep），存储更新结果和 diff，支持后续查看执行历史

#### Scenario: 版本更新错误处理
- **WHEN** 版本更新过程中发生错误（文件不存在、解析失败、权限不足等）
- **THEN** 系统捕获异常，记录错误信息到 RunStep，返回明确的错误提示，不修改原文件

### Requirement: 版本校验
系统 SHALL 支持在执行版本更新前，根据 VersionPolicy 和 BranchRule 推导并校验目标版本和分支名。

#### Scenario: 版本推导
- **WHEN** 用户请求版本校验，提供 ReleaseWindow 和关联的仓库
- **THEN** 系统根据 VersionPolicy 推导目标版本号，根据 BranchRule 推导分支名，返回推导结果

#### Scenario: 版本格式校验
- **WHEN** 系统推导出目标版本号
- **THEN** 系统校验版本号格式（SemVer、日期版本等），格式不正确时返回错误

#### Scenario: 版本冲突检测
- **WHEN** 系统推导出目标版本号
- **THEN** 系统检测是否存在相同版本号的发布窗口或运行记录，存在冲突时返回警告

#### Scenario: 分支名格式校验
- **WHEN** 系统推导出分支名
- **THEN** 系统根据 BranchRule 的正则表达式校验分支名格式，格式不正确时返回错误

### Requirement: 版本更新 API
系统 SHALL 提供 REST API 接口支持版本更新和校验操作。

#### Scenario: 执行版本更新
- **WHEN** 用户调用 `POST /api/v1/release-windows/{id}/execute/version-update`，提供目标版本和仓库信息
- **THEN** 系统执行版本更新，返回 runId 和执行状态

#### Scenario: 版本校验
- **WHEN** 用户调用 `POST /api/v1/release-windows/{id}/validate`，提供 ReleaseWindow ID
- **THEN** 系统执行版本和分支推导校验，返回校验结果（OK/ERROR + message）

### Requirement: 版本更新 UI
前端 SHALL 提供版本更新执行界面，支持查看执行结果和 diff。

#### Scenario: 执行版本更新
- **WHEN** 用户在发布窗口详情页点击"执行版本更新"按钮
- **THEN** 系统弹出确认对话框，显示目标版本和仓库列表，用户确认后执行更新，显示执行进度

#### Scenario: 查看执行结果
- **WHEN** 用户查看 Run 详情页
- **THEN** 系统展示版本更新的执行步骤、结果状态、diff 内容，支持展开/折叠 diff 查看

#### Scenario: 错误提示
- **WHEN** 版本更新执行失败
- **THEN** 系统在 UI 中显示明确的错误信息，包括错误类型和建议操作
