# 需求：GitFlow 分支生命周期管理

> 优先级：高
> 状态：**已完成（v0.1.5）**
> 完成时间：2026-03-02

## 背景

当前发布流程中的分支操作（创建 feature/release、合并、打标签、删除分支）在后端仍以 Mock 为主，无法对真实 GitHub/GitLab 仓库执行闭环。团队虽已在仓库级手动执行 GitFlow，但平台能力尚未打通。

## 目标

1. 平台支持真实 Git Provider（GitHub/GitLab）分支生命周期操作。
2. 发布窗口页面可视化展示分支状态。
3. 每个阶段开发完成后，团队实践上可稳定执行 `feature -> release -> tag -> main -> 清理分支`。

## 验收标准

- [x] Stage 1：`CodeRepository` 支持 `gitProvider` 与 `gitToken`，并完成数据库迁移。
- [x] Stage 2：完成 `GitBranchPort` 与 `GitHub/GitLab/Mock` Adapter。
- [x] Stage 3：RunTask Executors 切换到 `GitBranchPort`。
- [x] Stage 4：提供 `branch-status` API 与前端状态面板。
- [x] Stage 5（Section 8）：相关单元/集成/E2E 测试覆盖，发布流程可回归。

## 技术方案

- OpenSpec 提案：`openspec/changes/archive/2026-03-02-add-gitflow-branch-lifecycle/proposal.md`
- 实施策略：按 Stage 逐步落地，阶段结束即走 GitFlow 发布流程。

## 发布记录

| 版本 | 包含内容 | 日期 |
|------|----------|------|
| v0.1.1 | Stage 1: 数据模型扩展 | 2026-02-27 |
| v0.1.2 | Stage 2+3: Port/Adapter + Executor 切换 | 2026-03-02 |
| v0.1.3 | Stage 4a: branch-status API + 前端面板 | 2026-03-02 |
| v0.1.4 | Stage 4b: 仓库 Git 配置 UI | 2026-03-02 |
| v0.1.5 | Stage 8: 测试增强（18 个测试全覆盖）| 2026-03-02 |
