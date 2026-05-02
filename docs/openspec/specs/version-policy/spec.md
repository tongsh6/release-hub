# version-policy Specification

## Purpose
版本策略（VersionPolicy）定义代码仓库的版本号递增规则和构建工具配置。支持 SemVer（MAJOR.MINOR.PATCH）和 DATE（YYYY.MM.DD）两种版本方案，提供 MAJOR/MINOR/PATCH/NONE 四种 BumpRule。配合 VersionUpdater（Maven/Gradle 构建工具适配器）实现版本号的自动推导与文件更新。

## Requirements

### Requirement: 版本策略管理
系统 SHALL 支持版本策略的创建、更新、删除和分页查询。

#### Scenario: 创建版本策略
- **WHEN** 用户提交包含 name、versionScheme（SEMVER/DATE）、bumpRule（MAJOR/MINOR/PATCH/NONE）、buildTool（MAVEN/GRADLE）的创建请求
- **THEN** 系统校验必填字段并存储版本策略

#### Scenario: 分页与筛选
- **WHEN** 用户按 page/size 与 keyword（匹配 name）请求版本策略列表
- **THEN** 返回对应分页数据与 total 元信息

### Requirement: SemVer 版本推导
系统 SHALL 根据 BumpRule 对 SemVer 格式（X.Y.Z）版本号进行递增推导。

#### Scenario: MAJOR 递增
- **WHEN** 当前版本为 1.2.3，BumpRule 为 MAJOR
- **THEN** 推导版本为 2.0.0

#### Scenario: MINOR 递增
- **WHEN** 当前版本为 1.2.3，BumpRule 为 MINOR
- **THEN** 推导版本为 1.3.0

#### Scenario: PATCH 递增
- **WHEN** 当前版本为 1.2.3，BumpRule 为 PATCH
- **THEN** 推导版本为 1.2.4

#### Scenario: NONE 不变
- **WHEN** BumpRule 为 NONE
- **THEN** 推导版本与当前版本相同

### Requirement: DATE 版本推导
系统 SHALL 支持基于日期的版本方案，版本号格式为 YYYY.MM.DD。

#### Scenario: DATE 版本生成
- **WHEN** 版本策略 scheme 为 DATE
- **THEN** 系统根据当前日期生成格式为 YYYY.MM.DD 的版本号

### Requirement: SNAPSHOT 版本转换
系统 SHALL 在开发版本和目标版本之间进行 SNAPSHOT 转换。

#### Scenario: 开发版本推导
- **WHEN** 目标版本为 1.3.0
- **THEN** 开发版本为 1.3.0-SNAPSHOT

#### Scenario: 目标版本推导
- **WHEN** 开发版本为 1.3.0-SNAPSHOT
- **THEN** 目标版本为 1.3.0（移除 -SNAPSHOT 后缀）
