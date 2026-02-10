## ADDED Requirements

### Requirement: 发布窗口状态流转

发布窗口状态 SHALL 按 DRAFT → PUBLISHED → CLOSED 流转。

### Requirement: 迭代关联发布窗口时创建 release 分支

当迭代关联到发布窗口时，系统 SHALL 为迭代管理的所有代码仓库创建 release 分支，并在存在 feature/hotfix 分支时自动合并到 release。

#### Scenario: 首次关联迭代到窗口
- **GIVEN** 一个发布窗口 RW-20260115-ABCD，一个迭代 ITER-20260110-XYZ
- **AND** 该迭代关联了仓库 A 和仓库 B
- **WHEN** 用户将迭代关联到发布窗口
- **THEN** 系统为仓库 A 从 master 创建分支 release/RW-20260115-ABCD
- **AND** 系统为仓库 B 从 master 创建分支 release/RW-20260115-ABCD
- **AND** 若存在 feature/ITER-20260110-XYZ 或 hotfix/ITER-20260110-XYZ，则自动合并到 release/RW-20260115-ABCD

#### Scenario: release 分支已存在（多迭代关联同一窗口）
- **GIVEN** 发布窗口 RW-20260115-ABCD 已关联了迭代 A
- **AND** 仓库已存在 release/RW-20260115-ABCD 分支
- **WHEN** 用户将迭代 B 关联到同一发布窗口
- **THEN** 系统不重复创建 release 分支
- **AND** 系统将迭代 B 关联仓库的 feature/hotfix 分支合并到现有 release 分支

### Requirement: 发布准备合并功能（提测）

发布窗口 SHALL 提供手动合并能力，用于将 feature/hotfix 的最新代码合并到 release 分支。

#### Scenario: 手动触发代码合并（单个迭代）
- **GIVEN** 发布窗口 RW-20260115-ABCD，已关联迭代 ITER-20260110-XYZ
- **WHEN** 用户触发合并并选择迭代
- **THEN** 系统将该迭代所有仓库的 feature/hotfix 分支合并到 release 分支

### Requirement: 解除挂载归档

系统 SHALL 在解除挂载时按规则归档相关分支。

#### Scenario: 迭代从发布窗口解除挂载
- **GIVEN** 发布窗口与迭代已关联并创建 release 分支
- **WHEN** 用户解除挂载迭代
- **THEN** 系统归档对应 release 分支，归档原因 `unpublished`

#### Scenario: 仓库从迭代解除挂载
- **GIVEN** 迭代已挂载仓库并创建 feature/hotfix 分支
- **WHEN** 用户解除仓库挂载
- **THEN** 系统归档对应 feature/hotfix 分支，归档原因 `unpublished`

### Requirement: 发布收尾编排与任务追踪

收尾编排 SHALL 在“上线完成、关闭窗口前”由用户触发，系统异步执行并生成运行记录与任务列表。

### Requirement: 收尾任务类型

系统 SHALL 支持以下收尾任务类型，按顺序执行：

1. MERGE_RELEASE_TO_MASTER - release 分支合并到 master
2. ARCHIVE_BRANCHES - 归档 feature/hotfix 分支（归档原因为 released）
3. ARCHIVE_ITERATION - 归档关联迭代
