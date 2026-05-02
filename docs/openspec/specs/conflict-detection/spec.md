# conflict-detection Specification

## Purpose
冲突检测（Conflict Detection）在发布执行前对多维度进行预检，识别版本冲突、分支冲突、合并冲突和跨仓库一致性冲突（共 7 种冲突类型）。检测结果用于发布窗口的执行前阻断 UI（ConflictPanel），确保发布操作在一致的状态下进行，避免部分成功部分失败的不一致状态。

## Requirements

### Requirement: 冲突扫描
系统 SHALL 在发布执行前对发布窗口关联的所有仓库和迭代进行多维冲突扫描，覆盖以下 7 种冲突类型。

#### Scenario: 版本不匹配 (MISMATCH)
- **GIVEN** 系统记录的版本号为 1.3.0-SNAPSHOT
- **AND** 代码仓库 feature 分支的实际版本号为 1.2.0-SNAPSHOT
- **WHEN** 系统执行冲突扫描
- **THEN** 系统检测到版本不匹配冲突
- **AND** 在扫描结果中标记为 MISMATCH

#### Scenario: 仓库版本超前 (REPO_AHEAD)
- **GIVEN** 系统记录的版本号为 1.3.0-SNAPSHOT
- **AND** 代码仓库 feature 分支的实际版本号为 1.4.0-SNAPSHOT
- **WHEN** 系统执行冲突扫描
- **THEN** 系统检测到仓库版本比系统记录更新
- **AND** 在扫描结果中标记为 REPO_AHEAD

#### Scenario: 系统版本超前 (SYSTEM_AHEAD)
- **GIVEN** 系统记录的版本号为 1.4.0-SNAPSHOT
- **AND** 代码仓库 feature 分支的实际版本号为 1.3.0-SNAPSHOT
- **WHEN** 系统执行冲突扫描
- **THEN** 系统检测到系统记录的版本比仓库更新
- **AND** 在扫描结果中标记为 SYSTEM_AHEAD

#### Scenario: 目标分支已存在 (BRANCH_EXISTS)
- **GIVEN** 仓库 A 需要创建 release 分支 release/2026-W01
- **AND** 该 release 分支在仓库中已存在
- **WHEN** 系统执行冲突扫描
- **THEN** 系统检测到目标分支已存在
- **AND** 在扫描结果中标记为 BRANCH_EXISTS

#### Scenario: 分支名不合规 (BRANCH_NONCOMPLIANT)
- **GIVEN** 仓库 A 的 feature 分支名为 temp/fix-something
- **AND** BranchRule 配置要求 feature 分支必须以 `feature/` 开头
- **WHEN** 系统执行冲突扫描
- **THEN** 系统检测到分支名不符合规则
- **AND** 在扫描结果中标记为 BRANCH_NONCOMPLIANT

#### Scenario: 跨仓库版本不一致 (CROSS_REPO_VERSION_MISMATCH)
- **GIVEN** 发布窗口关联仓库 A（版本 1.2.3 → 1.3.0）和仓库 B（版本 2.0.0 → 2.1.0）
- **AND** 两个仓库在同一迭代中，预期同步发布
- **WHEN** 系统执行冲突扫描
- **THEN** 系统检测到仓库间版本跳幅不一致
- **AND** 标记为 CROSS_REPO_VERSION_MISMATCH

#### Scenario: Git 合并冲突 (MERGE_CONFLICT)
- **GIVEN** 仓库 A 的 feature 分支 merge 到 release 分支时存在代码冲突
- **WHEN** 系统执行冲突扫描（通过 checkMergeability 临时 MR/PR 预检）
- **THEN** 系统检测到合并冲突
- **AND** 在扫描结果中标记为 MERGE_CONFLICT

### Requirement: 冲突结果展示
系统 SHALL 在扫描结果中返回冲突列表，包含冲突类型、涉及仓库、严重程度和解决建议。

#### Scenario: 查看冲突结果
- **WHEN** 用户请求发布窗口的冲突扫描结果
- **THEN** 返回冲突列表，每条包含：
  - conflictType：冲突类型枚举
  - severity：INFO / WARNING / BLOCKING
  - involvedRepos：涉及的仓库 ID 列表
  - description：冲突描述
  - suggestion：解决建议

#### Scenario: 无冲突
- **GIVEN** 发布窗口关联的所有仓库版本一致、分支可合并、无跨仓库冲突
- **WHEN** 系统执行冲突扫描
- **THEN** 返回空冲突列表
- **AND** 标记为 READY_FOR_RELEASE

### Requirement: 执行前阻断
系统 SHALL 在存在 BLOCKING 级别冲突时阻止发布执行。

#### Scenario: 阻断发布
- **GIVEN** 冲突扫描结果中包含 BLOCKING 级别的冲突
- **WHEN** 用户尝试执行发布操作
- **THEN** 系统返回错误"存在未解决的阻断性冲突"
- **AND** 在前端 ConflictPanel 中高亮显示阻断项

#### Scenario: 非阻断性冲突允许继续
- **GIVEN** 冲突扫描结果中仅包含 WARNING 级别的冲突
- **WHEN** 用户尝试执行发布操作
- **THEN** 系统展示警告但允许用户确认后继续
