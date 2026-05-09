# Tasks: 迭代-仓库-分支三层关联与 feature 分支创建模式

## DAG

```
确认设计 (本文件)
  └── Slice 1: 领域层 — BranchCreationMode 枚举 + 保护分支校验
        └── Slice 2: 应用层 — setupRepoForIteration 重构 + 编排/Attach fallback 移除
              └── Slice 3: API 层 — 新增分支列表 API + 请求体变更
                    ├── Slice 4: 前端 — 分支创建模式选择器
                    └── Slice 5: 测试 — Surefire + 验收
```

## Slice 1: 领域层 — 新增枚举与校验

- [ ] 创建 `BranchCreationMode` 枚举（AUTO, NAMED, EXISTING）
- [ ] 在 `IterationRepoVersionInfo` 中新增 `branchCreationMode` 字段
- [ ] 实现 `validateFeaturePrefix()` 保护分支校验逻辑
- [ ] Flyway 迁移脚本：`V28__add_branch_creation_mode.sql`

## Slice 2: 应用层 — setupRepoForIteration 重构 + fallback 移除

- [ ] 重构 `setupRepoForIteration` 接受 `BranchCreationMode` + `customBranchName`
- [ ] 统一 `create()` 调用 `setupRepoForIteration`
- [ ] 统一 `update()` 中新增仓库调用 `setupRepoForIteration`
- [ ] 统一 `update()` 中移除仓库调用归档分支
- [ ] 保持 `repoIds` 向后兼容（无 `repoConfigs` 时默认 AUTO）
- [ ] `AttachAppService.setupReleaseBranchForRepo()`：移除 `"feature/" + iterationKey` fallback
- [ ] `RunAppService.startOrchestrate()`：移除 `"feature/" + iterationKey` fallback
- [ ] `ReleaseBranchService.createReleaseBranchAndMerge()`：移除 `"feature/" + iterationKey` fallback

## Slice 3: API 层 — 新增端点 + 请求体变更

- [ ] `GET /api/v1/repositories/{id}/branches?prefix=feature/` 新端点
- [ ] `GitLabGitBranchAdapter` 新增 `listBranches(cloneUrl, token, prefix)` 方法
- [ ] 修改 `CreateIterationRequest` 支持 `repoConfigs`
- [ ] 修改 `UpdateIterationRequest` 支持 `repoConfigs`
- [ ] `RepoChangeRequest` 新增 `branchCreationMode` + `customBranchName`（仅 `addRepos` 有效）

## Slice 4: 前端 — 分支创建模式选择器

- [ ] 迭代创建/编辑页面新增 `BranchCreationMode` 下拉选择器
- [ ] AUTO 模式：显示预览分支名
- [ ] NAMED 模式：展开文本输入框，前端校验 `feature/` 前缀
- [ ] EXISTING 模式：调用分支列表 API，展示下拉列表
- [ ] i18n（zh-CN + en-US）

## Slice 5: 测试与验收

- [ ] 后端 Surefire：`BranchCreationMode` + 保护分支校验单测
- [ ] 后端 Surefire：`setupRepoForIteration` 三种模式 + 异常路径单测
- [ ] 验收：三种模式分别创建迭代，验证分支创建/映射正确
- [ ] 验收：`feature/` 路径外的分支名被拒绝
- [ ] 验收：`create`/`addRepos`/`update` 三个入口行为一致
- [ ] 验收：编排时 featureBranch 已配置 → 正确查找
- [ ] 验收：编排时 featureBranch 未配置 → SKIP + 原因明确

## 未完成项追踪

（本需求全部任务已在此 DAG 中覆盖）
