# Tasks: 迭代-仓库-分支三层关联与 feature 分支创建模式

> **状态：✅ 已完成（2026-05-11）**
> 实现 commit：`931a6b3`（功能落地）+ `0e2efb3`（多 Provider 端口收口）
> 真实 GitLab 验收：`docs/reports/acceptance-v0.1.11-real-gitlab.md` 场景 10.1-10.5 全 PASS

## DAG

```
确认设计 (本文件)
  └── Slice 1: 领域层 — BranchCreationMode 枚举 + 保护分支校验
        └── Slice 2: 应用层 — setupRepoForIteration 重构 + 编排/Attach fallback 移除
              └── Slice 3: API 层 — 新增分支列表 API + 请求体变更
                    ├── Slice 4: 前端 — 分支创建模式选择器
                    └── Slice 5: 测试 — Surefire + 验收
```

## Slice 1: 领域层 — 新增枚举与校验 ✅

- [x] 创建 `BranchCreationMode` 枚举（AUTO, NAMED, EXISTING） — `domain/iteration/BranchCreationMode.java`
- [x] 在 `IterationRepoVersionInfo` 中新增 `branchCreationMode` 字段（通过 `iteration_repo.branch_creation_mode` 列承接）
- [x] 实现 `validateFeaturePrefix()` 保护分支校验逻辑 — `IterationAppService.validateFeaturePrefix()`
- [x] Flyway 迁移脚本：`V28__add_branch_creation_mode.sql`

## Slice 2: 应用层 — setupRepoForIteration 重构 + fallback 移除 ✅

- [x] 重构 `setupRepoForIteration` 接受 `BranchCreationMode` + `customBranchName` — `IterationAppService.setupRepoForIteration()` switch 三模式
- [x] 统一 `create()` 调用 `setupRepoForIteration`
- [x] 统一 `update()` 中新增仓库调用 `setupRepoForIteration`
- [x] 统一 `update()` 中移除仓库调用归档分支
- [x] 保持 `repoIds` 向后兼容（无 `repoConfigs` 时默认 AUTO） — `resolveRepoConfigs()`
- [x] `AttachAppService.setupReleaseBranchForRepo()`：移除 `"feature/" + iterationKey` fallback
- [x] `RunAppService.startOrchestrate()`：移除 `"feature/" + iterationKey` fallback
- [x] `ReleaseBranchService.createReleaseBranchAndMerge()`：移除 `"feature/" + iterationKey` fallback

## Slice 3: API 层 — 新增端点 + 请求体变更 ✅

- [x] `GET /api/v1/repositories/{id}/branches?prefix=feature/` 新端点 — 验收脚本场景 10.5 PASS
- [x] `GitLabGitBranchAdapter.listBranches(cloneUrl, token, prefix)` 方法 — 多 Provider 收口后由 `GitBranchPort.listBranches()` 提供
- [x] 修改 `CreateIterationRequest` 支持 `repoConfigs`
- [x] 修改 `UpdateIterationRequest` 支持 `repoConfigs`
- [x] `RepoChangeRequest` 新增 `branchCreationMode` + `customBranchName`

## Slice 4: 前端 — 分支创建模式选择器 ✅

- [x] 迭代创建/编辑页面新增 `BranchCreationMode` 下拉选择器（`AddReposDialog`）
- [x] AUTO/NAMED/EXISTING 三种模式 UI 接入
- [x] `frontend/src/api/iterationApi.ts` 携带 `branchCreationMode` + `customBranchName`
- [x] i18n（zh-CN + en-US）`branchCreationMode.*` 完整覆盖

## Slice 5: 测试与验收 ✅

- [x] 后端 Surefire：`BranchCreationMode` + 保护分支校验单测 — IterationAppServiceTest 15 用例覆盖
- [x] 后端 Surefire：`setupRepoForIteration` 三种模式 + 异常路径单测
- [x] 验收：三种模式分别创建迭代，验证分支创建/映射正确 — 报告场景 10.1 / 10.2 / 10.4
- [x] 验收：`feature/` 路径外的分支名被拒绝 — 报告场景 10.3
- [x] 验收：`create`/`addRepos`/`update` 三个入口行为一致 — IterationAppServiceTest `RepoBranchConfig` 覆盖
- [x] 验收：编排时 featureBranch 已配置 → 正确查找
- [⚠️] 验收：编排时 featureBranch 未配置 → SKIP + 原因明确 — 链路具备但被「GitLab Settings missing」P1 阻塞，未跑到该路径，待脚本 v3.1 修复后重跑

## 后续动作（v0.1.11 验收报告衍生）

- 脚本 v3.1：`run-acceptance.sh` 场景 2 末尾增加 `POST /settings/gitlab` 一步
- 代码 P0：`WindowLifecycleListener` 异常隔离（避免 `UnexpectedRollbackException`）
- 重跑验收：闭掉 v0.1.10 报告中「编排 0 items」「VERSION_UPDATE FAILED」两处已知限制
