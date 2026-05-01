# ReleaseHub 下一步任务清单

> 最后更新：2026-02-27

## 当前执行主线

### P0：GitFlow 分支生命周期（平台能力）

关联：
- 需求：`requirements/in-progress/GitFlow分支生命周期管理.md`
- OpenSpec：`openspec/changes/add-gitflow-branch-lifecycle/`

阶段拆分：
- [x] Stage 1：仓库 Git 配置字段（`gitProvider/gitToken`）+ 迁移
- [ ] Stage 2：`GitBranchPort` + `GitHub/GitLab/Mock Adapter` + `Factory`
- [ ] Stage 3：RunTask Executors 切换到 `GitBranchPort`
- [ ] Stage 4：`branch-status` API + 前端状态面板
- [ ] Stage 5：单元/集成/E2E 验证与发布验收

## 并行需求线

### P1：版本更新功能增强

- [ ] 多模块 Maven 版本一致性
- [ ] 分支推导服务（依赖 BranchRule）
- [ ] 冲突检测增强
- [ ] 前端 E2E 覆盖

关联：`requirements/in-progress/版本更新功能增强.md`

### P1：发布协调日历视图增强

- [x] 月视图与路由菜单基础能力
- [ ] 周视图
- [ ] 冲突可视化提示

关联：`requirements/in-progress/发布协调日历视图.md`

## GitFlow 执行规范（阶段完成后必须执行）

1. `feature/*` 开发并通过验证
2. 合并到 `release/vX.Y.Z`
3. 在 release 分支打标签 `vX.Y.Z`
4. `release/vX.Y.Z` 合并到 `main`
5. 删除 `feature/*` 与 `release/*` 分支

## 验收口径

每个阶段完成时，必须同时满足：

- [ ] 代码实现完成并验证通过（compile/test）
- [ ] OpenSpec `tasks.md` 勾选同步
- [ ] `requirements/INDEX.md` 与需求文档状态同步
- [ ] 版本标签与分支清理完成
