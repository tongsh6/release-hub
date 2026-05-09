# iteration Specification Delta

## MODIFIED Requirements

### Requirement: 仓库关联迭代时创建 feature 分支

当仓库关联到迭代时，系统 SHALL 根据用户选择的分支创建模式（BranchCreationMode）执行对应操作。

#### Scenario: AUTO 模式 — 系统自动创建 feature 分支

- **GIVEN** 用户选择 AUTO 模式，将仓库关联到迭代 ITER-20260115-ABCD
- **WHEN** 仓库关联到迭代
- **THEN** 系统从 master 创建分支 `feature/ITER-20260115-ABCD`
- **AND** 系统将 `featureBranch` 设置为 `feature/ITER-20260115-ABCD`
- **AND** 系统推导版本号并写入 `IterationRepoVersionInfo`

#### Scenario: NAMED 模式 — 用户自定义 feature 分支名

- **GIVEN** 用户选择 NAMED 模式，输入分支名 `feature/upgrade-guava`
- **WHEN** 仓库关联到迭代
- **THEN** 系统校验分支名以 `feature/` 开头
- **AND** 系统校验分支名不包含 `release/`、`hotfix/` 保护前缀
- **AND** 系统校验分支名符合 BranchRule 规则
- **AND** 系统从 master 创建分支 `feature/upgrade-guava`
- **AND** 系统将 `featureBranch` 设置为 `feature/upgrade-guava`

#### Scenario: NAMED 模式 — 分支名不在 feature/ 路径下被拒绝

- **GIVEN** 用户选择 NAMED 模式，输入分支名 `hotfix/critical-fix`
- **WHEN** 仓库关联到迭代
- **THEN** 系统拒绝，返回错误 "分支必须在 feature/ 路径下"
- **AND** 不创建任何分支

#### Scenario: EXISTING 模式 — 关联已有 feature 分支

- **GIVEN** 用户选择 EXISTING 模式，选择已有分支 `feature/JIRA-4521`
- **WHEN** 仓库关联到迭代
- **THEN** 系统校验分支在 GitLab 中确实存在
- **AND** 系统校验分支名以 `feature/` 开头
- **AND** 系统不创建新分支
- **AND** 系统将 `featureBranch` 设置为 `feature/JIRA-4521`

#### Scenario: EXISTING 模式 — 分支不存在被拒绝

- **GIVEN** 用户选择 EXISTING 模式，选择分支 `feature/nonexistent`
- **WHEN** 仓库关联到迭代
- **THEN** 系统通过 GitLab API 查询分支，返回 404
- **AND** 系统拒绝，返回错误 "分支不存在: feature/nonexistent"

#### Scenario: 版本号升级规则不变

- **GIVEN** 任意分支创建模式
- **WHEN** 建立 feature 分支映射后
- **THEN** 版本推导规则不变：devVersion = X.(Y+1).0-SNAPSHOT
- **AND** targetVersion = X.(Y+1).0

## ADDED Requirements

### Requirement: 列出仓库 feature 路径下的分支

系统 SHALL 提供 API 查询仓库 `feature/` 路径下的分支列表，用于 EXISTING 模式的分支选择。

#### Scenario: 查询 feature 分支列表

- **GIVEN** 仓库 `user-service` 在 GitLab 上有分支 `feature/JIRA-123`、`feature/refactor`、`release/1.0`、`master`
- **WHEN** 调用 `GET /api/v1/repositories/{id}/branches?prefix=feature/`
- **THEN** 返回 `["feature/JIRA-123", "feature/refactor"]`
- **AND** 不返回 `release/1.0`、`master`

#### Scenario: 查询 feature 分支列表为空

- **GIVEN** 仓库在 GitLab 上没有 `feature/` 路径下的分支
- **WHEN** 调用 `GET /api/v1/repositories/{id}/branches?prefix=feature/`
- **THEN** 返回空列表 `[]`

### Requirement: 系统兜底校验 — 分支路径隔离

所有涉及分支操作的 API SHALL 校验目标分支在 `feature/` 路径下，不可涉及保护分支。

#### Scenario: 保护分支前缀黑名单

- **GIVEN** 系统硬性要求所有 feature 分支操作必须在 `feature/` 路径下
- **WHEN** 任何分支创建或关联操作
- **THEN** 如果目标分支名不以 `feature/` 开头，拒绝操作
- **AND** 系统额外检查分支名不包含 `release/`、`hotfix/` 保护前缀（防御性编程）

#### Scenario: 分支规则校验

- **GIVEN** BranchRule 已配置 TEMPLATE 规则：`feature/*`
- **WHEN** 用户输入分支名 `feature/fix`
- **THEN** 系统调用 `branchRuleUseCase.isCompliant("feature/fix")` 校验通过

### Requirement: 编排时 featureBranch 必须已配置

编排和 Attach 流程中，feature 分支名 SHALL 从 `IterationRepoVersionInfo.featureBranch` 读取，不再使用 `"feature/{iterationKey}"` 作为 fallback。

#### Scenario: 编排时 featureBranch 已配置

- **GIVEN** 迭代 ITER-xxx 的仓库 A 已配置 `featureBranch = "feature/JIRA-4521"`
- **WHEN** 执行编排或 Attach
- **THEN** 系统读取 `featureBranch = "feature/JIRA-4521"`
- **AND** 在 GitLab API 中查找此分支
- **AND** 不 fallback 到 `feature/ITER-xxx`

#### Scenario: 编排时 featureBranch 未配置

- **GIVEN** 迭代 ITER-xxx 的仓库 A 未配置 `featureBranch`（值为 null）
- **WHEN** 执行编排或 Attach
- **THEN** 系统跳过此仓库
- **AND** 生成 RunItem 标记为 SKIPPED，原因 "featureBranch 未配置"

### Requirement: 三个入口行为一致性

`create()`、`addRepos()`、`update()` 三个 API 入口在"仓库绑定迭代"这个语义上 SHALL 行为一致。

#### Scenario: create 带 repoIds 时的分支创建

- **GIVEN** 用户调用 `POST /api/v1/iterations` 创建迭代，附带 repoIds 和分支创建模式
- **WHEN** 创建成功
- **THEN** 系统为每个仓库执行 `setupRepoForIteration`，行为与 `addRepos` 一致
- **AND** 写入 `IterationRepoVersionInfo`

#### Scenario: update 新增仓库时的分支创建

- **GIVEN** 已存在的迭代 ITER-xxx，原有关联仓库 [A]
- **WHEN** 用户调用 `PUT /api/v1/iterations/{key}` 将 repoIds 改为 [A, B]
- **THEN** 系统为新增的仓库 B 执行 `setupRepoForIteration`
- **AND** 仓库 A 不受影响

#### Scenario: update 移除仓库时的分支归档

- **GIVEN** 已存在的迭代 ITER-xxx，原有关联仓库 [A, B]
- **WHEN** 用户调用 `PUT /api/v1/iterations/{key}` 将 repoIds 改为 [A]
- **THEN** 系统归档仓库 B 对应的 feature 分支（archiveBranch，原因 "unpublished"）
