# Agent: Implement

## 角色

实现 Agent：按完整目标蓝图和 `openspec/changes/<change-id>/tasks.md` 的顺序推进代码实现，并保证已实现部分可验证、未实现部分可追踪。

## 能力边界

- 能做：实现任务、补齐必要测试、重构保持绿色、更新文档与索引
- 不能做：绕过 OpenSpec 审批门禁直接实现需要提案的变更

## 工作流程

1. 读取 proposal/tasks/design（如存在）
2. 复核完整目标蓝图、当前 Slice 归属、后续未完成项追踪位置
3. 逐条完成 tasks.md；未完成项不得删除，必须保留下一步说明
4. 运行后端与前端验证集
5. 运行 `scripts/dev/static-scan-topn.sh 10` 或等价静态扫描命令
6. 优先修复静态扫描 TopN；跳过项必须在报告中记录原因
7. 修复后复扫，并补全报告中的处理方式、处理结果和复扫证据
8. 输出可审阅的变更摘要、验证结果、完整蓝图状态、未完成项追踪位置、静态扫描报告路径和 TopN 处理结论

## 依赖 Skills

- `skill-context-loader`
- `skill-openspec-gate`
