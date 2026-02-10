## ADDED Requirements

### Requirement: 仓库关联迭代时创建 feature 分支

当仓库关联到迭代时，系统 SHALL 从 master 分支创建 feature 分支，并设置开发版本号。

#### Scenario: 创建 feature 分支并设置版本

- **GIVEN** 一个代码仓库，master 分支版本为 1.2.3
- **WHEN** 该仓库关联到迭代 ITER-20260115-ABCD
- **THEN** 系统从 master 创建分支 feature/ITER-20260115-ABCD
- **AND** 系统将该分支的版本号升级为 1.3.0-SNAPSHOT（中间版本号 +1）
- **AND** 系统保存基准版本(1.2.3)、开发版本(1.3.0-SNAPSHOT)、目标版本(1.3.0)到关联记录

#### Scenario: 版本号升级规则

- **GIVEN** master 分支版本为 X.Y.Z
- **WHEN** 创建 feature 分支时
- **THEN** 开发版本为 X.(Y+1).0-SNAPSHOT
- **AND** 目标版本为 X.(Y+1).0

### Requirement: 仓库初始版本号管理

系统 SHALL 在代码仓库新建时获取或设置初始版本号。

#### Scenario: 从 master 获取初始版本号

- **GIVEN** 新建一个代码仓库
- **WHEN** 仓库创建完成
- **THEN** 系统从 master 分支的 pom.xml/gradle.properties 获取版本号
- **AND** 存储为该仓库的初始版本号

#### Scenario: 手动设置初始版本号

- **GIVEN** 新建一个代码仓库
- **WHEN** 用户提供初始版本号参数
- **THEN** 系统使用用户提供的版本号作为初始版本号
- **AND** 跳过从代码仓库获取版本号

#### Scenario: 获取版本号失败

- **GIVEN** 新建一个代码仓库
- **WHEN** 从 master 分支获取版本号失败（文件不存在或解析失败）
- **THEN** 系统记录警告日志
- **AND** 初始版本号设置为空，等待手动设置

### Requirement: 迭代关联仓库版本号来源

系统 SHALL 支持从系统存储或代码仓库实时获取版本号，并处理版本冲突。

#### Scenario: 从系统获取版本号

- **GIVEN** 一个仓库已关联到迭代，系统中存储了版本号
- **WHEN** 用户查看迭代详情
- **THEN** 默认显示系统中存储的版本号

#### Scenario: 从代码仓库实时获取版本号

- **GIVEN** 一个仓库已关联到迭代
- **WHEN** 用户请求同步版本号
- **THEN** 系统从 feature 分支实时获取当前版本号
- **AND** 更新系统中存储的版本号

#### Scenario: 检测版本冲突

- **GIVEN** 系统存储的版本号为 1.3.0-SNAPSHOT
- **AND** feature 分支的实际版本号为 1.4.0-SNAPSHOT（被人手动修改）
- **WHEN** 用户请求同步版本号
- **THEN** 系统检测到版本不一致
- **AND** 提示用户选择：使用系统版本 / 使用代码仓库版本 / 取消操作

#### Scenario: 解决版本冲突 - 使用系统版本

- **GIVEN** 检测到版本冲突
- **WHEN** 用户选择"使用系统版本"
- **THEN** 系统将 feature 分支的版本号修改为系统存储的版本号
- **AND** 提交变更到代码仓库

#### Scenario: 解决版本冲突 - 使用代码仓库版本

- **GIVEN** 检测到版本冲突
- **WHEN** 用户选择"使用代码仓库版本"
- **THEN** 系统更新存储的版本号为代码仓库的版本号

### Requirement: 迭代-仓库版本号展示

系统 SHALL 在迭代详情中展示关联仓库的完整版本信息。

#### Scenario: 查看迭代关联仓库版本

- **GIVEN** 一个迭代，关联了多个仓库
- **WHEN** 用户查看迭代详情
- **THEN** 显示每个仓库的：
  - 基准版本（base_version）：关联时 master 的版本
  - 开发版本（dev_version）：feature 分支当前版本（含 SNAPSHOT）
  - 目标版本（target_version）：发布时的正式版本
  - 版本来源（version_source）：SYSTEM / REPO
