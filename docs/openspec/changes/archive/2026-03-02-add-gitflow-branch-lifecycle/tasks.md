# Tasks: GitFlow 分支生命周期管理

## 1. 数据模型扩展

- [x] 1.1 Flyway 迁移：`code_repository` 表添加 `git_provider` VARCHAR(20)、`git_token` VARCHAR(500) 字段
- [x] 1.2 `CodeRepositoryJpaEntity` 添加对应字段
- [x] 1.3 `CodeRepository` 领域对象添加 `gitProvider`、`gitToken` 字段
- [x] 1.4 `CodeRepositoryAppService` 创建/更新时支持传入上述字段
- [x] 1.5 `CodeRepositoryController` 和 DTO（`CreateCodeRepositoryRequest` / `UpdateCodeRepositoryRequest`）添加字段

## 2. GitBranchPort 接口

- [x] 2.1 创建 `GitBranchPort` 接口（取代现有各 Mock 接口），方法：
  - `createBranch(repoUrl, token, branchName, fromBranch)`
  - `deleteBranch(repoUrl, token, branchName)`
  - `mergeBranch(repoUrl, token, sourceBranch, targetBranch)` → `MergeResult`
  - `createTag(repoUrl, token, tagName, ref, message)`
  - `getBranchStatus(repoUrl, token, branchName)` → `BranchStatus`
- [x] 2.2 创建 `GitProvider` 枚举（`GITHUB` / `GITLAB` / `MOCK`）
- [x] 2.3 创建 `MergeResult` DTO（成功/冲突/失败 + 详情）— 实现为 `GitBranchPort.MergeResult` record
- [x] 2.4 创建 `BranchStatus` DTO（存在/不存在、最新 commit SHA、ahead/behind）— 实现为 `GitBranchPort.BranchStatus` record

## 3. Adapter 实现

- [x] 3.1 `GitHubBranchAdapter` 实现 `GitBranchPort`（GitHub REST API v3）
  - [x] 3.1.1 `createBranch` — `POST /repos/{owner}/{repo}/git/refs`
  - [x] 3.1.2 `deleteBranch` — `DELETE /repos/{owner}/{repo}/git/refs/heads/{branch}`
  - [x] 3.1.3 `mergeBranch` — `POST /repos/{owner}/{repo}/merges`
  - [x] 3.1.4 `createTag` — `POST /repos/{owner}/{repo}/git/tags`
  - [x] 3.1.5 `getBranchStatus` — `GET /repos/{owner}/{repo}/branches/{branch}`
- [x] 3.2 `GitLabBranchAdapter` 实现 `GitBranchPort`（GitLab REST API v4）
  - [x] 3.2.1–3.2.5 对应方法实现
- [x] 3.3 `MockGitBranchAdapter` 替代现有所有 Mock 实现
- [x] 3.4 `GitBranchAdapterFactory` — 根据 `gitProvider` 返回对应 Adapter（接口在 application 层，实现在 infrastructure 层）

## 4. 切换 RunTask Executor

- [x] 4.1 `CreateReleaseBranchExecutor` 从 Mock 切换到 `GitBranchAdapterFactory`
- [x] 4.2 `ArchiveFeatureBranchExecutor`（删除分支）切换
- [x] 4.3 `MergeFeatureToReleaseExecutor` 切换
- [x] 4.4 `MergeReleaseToMasterExecutor` 切换
- [x] 4.5 `CreateTagExecutor` 切换

## 5. 分支状态 API

- [x] 5.1 创建 `BranchStatusView` DTO（仓库 ID/名称、feature 分支列表、release 分支、合并状态）
- [x] 5.2 `ReleaseWindowController` 添加 `GET /api/v1/release-windows/{id}/branch-status`
- [x] 5.3 `ReleaseWindowAppService` 实现 `getBranchStatus(windowId)` 方法（调用 GitBranchPort）

## 6. 前端：分支状态面板

- [x] 6.1 在发布窗口详情页添加"分支状态"Panel
- [x] 6.2 实现 `BranchStatusPanel` 组件
  - [x] 6.2.1 表格列：仓库名、feature 分支列表、release 分支、合并状态 badge
  - [x] 6.2.2 合并状态颜色标识（PENDING=灰、MERGED=绿、CONFLICT=红）
  - [x] 6.2.3 支持手动刷新
- [x] 6.3 API 模块添加 `getBranchStatus(windowId)` 方法
- [x] 6.4 手动添加 TypeScript 类型定义（后端未启动时 gen:api 不可用）

## 7. 前端：仓库 Git 配置

- [x] 7.1 在"新建/编辑仓库"对话框添加"Git 配置"折叠区
  - `gitProvider` 下拉（GitHub / GitLab / Mock）
  - `gitToken` 密码输入框
- [x] 7.2 Token 在展示时脱敏（只显示前 4 位 + `****`）

## 8. 测试

- [x] 8.1 `GitHubBranchAdapter` 单元测试（Mock HTTP 客户端）
- [x] 8.2 `GitLabBranchAdapter` 单元测试
- [x] 8.3 `GitBranchAdapterFactory` 单元测试
- [x] 8.4 `BranchStatusView` API 集成测试（MockMvc）
- [x] 8.5 各 Executor 切换后集成测试（使用 `MockGitBranchAdapter`）
- [x] 8.6 E2E 测试：发布窗口发布流程（Mock Provider）

## 发布记录

| 版本 | 包含内容 | 日期 |
|------|----------|------|
| v0.1.1 | Stage 1: 数据模型扩展 | 2026-02-27 |
| v0.1.2 | Stage 2+3: Port/Adapter + Executor 切换 | 2026-03-02 |
| v0.1.3 | Stage 4a: branch-status API + 前端面板 | 2026-03-02 |
| v0.1.4 | Stage 4b: 仓库 Git 配置 UI | 2026-03-02 |
| v0.1.5 | Stage 8: 测试增强（18 个测试全覆盖）| 2026-03-02 |
