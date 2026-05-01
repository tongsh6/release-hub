# Change: GitFlow 分支生命周期管理

> 关联需求：`requirements/in-progress/GitFlow分支生命周期管理.md`

## Why

平台已有发布自动化框架（RunTask + 各 Executor），但分支操作（创建 feature、创建 release、合并、打标签、删除分支）全是 Mock 实现，无法在真实仓库上执行。当前分支状态也没有可视化界面，用户需手工在 Git 平台确认进度。

本变更将：
1. 将 Mock 分支操作替换为真实 GitHub/GitLab API 调用（可按仓库配置 Provider）
2. 在发布窗口详情页增加"分支状态"面板，实时展示各仓库 feature / release 分支的当前状态

## What Changes

### Git Provider 配置
- **新增**：`CodeRepository` 新增 `gitProvider` 字段（`GITHUB` / `GITLAB` / `MOCK`）
- **新增**：`CodeRepository` 新增 `gitToken` 字段（加密存储）
- **新增**：Flyway 迁移脚本，为 `code_repository` 表添加上述字段

### 真实分支 Adapter
- **新增**：`GitBranchPort` 统一接口，取代现有 `MockGitLabBranchService`
- **新增**：`GitHubBranchAdapter` 实现（调用 GitHub REST API v3）
- **新增**：`GitLabBranchAdapter` 实现（调用 GitLab REST API v4）
- **修改**：各 RunTask Executor（`CreateReleaseBranchExecutor`、`ArchiveFeatureBranchExecutor`、`MergeFeatureToReleaseExecutor`、`MergeReleaseToMasterExecutor`、`CreateTagExecutor`）从 Mock 切换到真实 Adapter
- **新增**：`GitBranchAdapterFactory`，根据仓库 `gitProvider` 选择对应 Adapter

### 分支状态 API
- **新增**：`GET /api/v1/release-windows/{id}/branch-status` 返回窗口内各仓库分支状态
- **新增**：`BranchStatusView`（仓库、feature 分支列表、release 分支、合并状态、最新提交）

### 前端：分支状态面板
- **新增**：发布窗口详情页"分支状态"Tab，展示每个仓库的分支状态矩阵
- **新增**：列：仓库名、feature 分支、release 分支、合并状态（PENDING / MERGED / CONFLICT）

## Impact

- Affected specs: gitflow（新建）, release-window, code-repository
- Affected code:
  - `releasehub-domain/`: `CodeRepository`（添加字段）
  - `releasehub-application/`: `GitBranchPort`（接口）、各 Executor（切换实现）
  - `releasehub-infrastructure/`: `GitHubBranchAdapter`、`GitLabBranchAdapter`、`GitBranchAdapterFactory`、Flyway 脚本
  - `releasehub-interfaces/`: `ReleaseWindowController`（新增 branch-status 端点）
  - `release-hub-web/`: 发布窗口详情页增加"分支状态"Tab
- Breaking changes: 无（Mock 为默认值，现有功能不受影响）
