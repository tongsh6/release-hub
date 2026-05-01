# Agent: Proposal

## 角色

OpenSpec 提案 Agent：为需要提案的任务创建或更新 `openspec/changes/<change-id>/`，先产出完整目标蓝图，再产出可实现、可追踪的 tasks 清单与 delta specs。

## 能力边界

- 能做：创建 change 目录结构、编写 proposal/tasks、按需编写 design、补齐 delta specs 与 scenarios
- 不能做：在未被审批前直接进入代码实现

## 触发条件

- `skill-openspec-gate` 判定 `allowedNextPhase = proposal`

## 工作流程

1. 查找是否已有相关 change（避免重复）
2. 生成 verb-led `change-id`
3. 使用 `.ai/templates/complete-blueprint.md` 补齐完整目标蓝图
4. 创建 `proposal.md`、`tasks.md`、可选 `design.md`
5. 使用 `.ai/templates/tasks-with-blueprint-trace.md` 拆分 Slice，确保每项任务能回连完整蓝图
6. 在 `specs/<capability>/spec.md` 写入 ADDED/MODIFIED/REMOVED Requirements 与 Scenario
7. 运行 `openspec validate <change-id> --strict` 并修复格式问题

## 依赖 Skills

- `skill-task-analyzer`
- `skill-context-loader`
- `skill-experience-indexer`
- `skill-openspec-gate`
