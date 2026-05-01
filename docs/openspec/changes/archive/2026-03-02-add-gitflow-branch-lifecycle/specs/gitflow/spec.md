## ADDED Requirements

### Requirement: Git Provider Configuration
每个 `CodeRepository` SHALL 支持配置 Git Provider（`GITHUB` / `GITLAB` / `MOCK`）和 Personal Access Token，用于分支生命周期操作。

#### Scenario: 配置 GitHub Provider
- **WHEN** 用户在创建/编辑仓库时选择 `gitProvider=GITHUB` 并填写 `gitToken`
- **THEN** 系统保存配置，后续分支操作使用 GitHub REST API

#### Scenario: 未配置 Provider 时默认 Mock
- **WHEN** 仓库未设置 `gitProvider`
- **THEN** 系统使用 `MockGitBranchAdapter`，分支操作仅记录日志不实际执行

#### Scenario: Token 展示脱敏
- **WHEN** 用户查询仓库详情
- **THEN** `gitToken` 字段仅返回前 4 位字符加 `****`，不返回完整 Token

---

### Requirement: Real Git Branch Operations
系统 SHALL 通过 `GitBranchPort` 对配置了真实 Provider 的仓库执行分支操作，包括创建分支、删除分支、合并分支、创建标签。

#### Scenario: 创建 feature 分支
- **WHEN** 仓库关联到迭代，且 `gitProvider=GITHUB`
- **THEN** 系统调用 GitHub API 从 `master` 创建 `feature/{iterationKey}` 分支

#### Scenario: 创建 release 分支
- **WHEN** 迭代关联到发布窗口，且仓库 `gitProvider != MOCK`
- **THEN** 系统调用对应 Git API 从 `master` 创建 `release/{windowKey}` 分支

#### Scenario: 合并 feature 到 release
- **WHEN** 触发"代码合并"操作
- **THEN** 系统调用 Git API 将 `feature/{iterationKey}` 合并到 `release/{windowKey}`
- **THEN** 若合并冲突，返回 `MergeResult.CONFLICT` 并记录详情

#### Scenario: 发布完成后打标签并合并主干
- **WHEN** 发布窗口状态变为 `PUBLISHED` 且 RunTask 执行完毕
- **THEN** 系统依次：合并 release → master、创建版本标签、删除 feature 和 release 分支

#### Scenario: Git API 调用失败时 RunTask 标记 FAILED
- **WHEN** Git API 返回错误（网络超时、权限不足等）
- **THEN** 对应 RunTask 状态变为 `FAILED`，保留错误信息
- **THEN** 现有 RetryPolicy 可触发重试

---

### Requirement: Branch Status Dashboard
发布窗口详情页 SHALL 提供"分支状态"面板，实时展示窗口内各仓库分支的生命周期状态。

#### Scenario: 查看分支状态
- **WHEN** 用户在发布窗口详情页切换到"分支状态"Tab
- **THEN** 系统调用 `GET /api/v1/release-windows/{id}/branch-status`
- **THEN** 展示每个仓库的 feature 分支列表、release 分支、各分支合并状态

#### Scenario: 合并状态颜色标识
- **WHEN** 分支状态面板加载完成
- **THEN** `MERGED` 显示绿色标签，`PENDING` 显示灰色，`CONFLICT` 显示红色

#### Scenario: 手动刷新
- **WHEN** 用户点击"刷新"按钮
- **THEN** 系统重新调用 Git API 获取最新分支状态并更新面板
