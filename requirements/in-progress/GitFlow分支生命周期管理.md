# 需求：GitFlow 分支生命周期管理

> 优先级：高
> 状态：进行中（Stage 1 已完成）

## 背景

当前发布流程中的分支操作（创建 feature/release、合并、打标签、删除分支）在后端仍以 Mock 为主，无法对真实 GitHub/GitLab 仓库执行闭环。团队虽已在仓库级手动执行 GitFlow，但平台能力尚未打通。

## 目标

1. 平台支持真实 Git Provider（GitHub/GitLab）分支生命周期操作。
2. 发布窗口页面可视化展示分支状态。
3. 每个阶段开发完成后，团队实践上可稳定执行 `feature -> release -> tag -> main -> 清理分支`。

## 验收标准

- [x] Stage 1：`CodeRepository` 支持 `gitProvider` 与 `gitToken`，并完成数据库迁移。
- [ ] Stage 2：完成 `GitBranchPort` 与 `GitHub/GitLab/Mock` Adapter。
- [ ] Stage 3：RunTask Executors 切换到 `GitBranchPort`。
- [ ] Stage 4：提供 `branch-status` API 与前端状态面板。
- [ ] Stage 5：相关单元/集成测试覆盖，发布流程可回归。

## 技术方案

- OpenSpec 提案：`openspec/changes/add-gitflow-branch-lifecycle/proposal.md`
- 实施策略：按 Stage 逐步落地，阶段结束即走 GitFlow 发布流程。

## 进度

- [x] Stage 1：仓库 Git 配置能力（后端 `release-hub` 已发布 `v0.1.1`）
- [ ] Stage 2：Port + Adapter
- [ ] Stage 3：Executor 切换
- [ ] Stage 4：分支状态 API + 前端面板
- [ ] Stage 5：测试与验收
