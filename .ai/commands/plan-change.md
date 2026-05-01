# Command: Plan Change

## 用法

```
/plan-change [变更目标]
```

## 功能

显式创建 OpenSpec change：用于新功能、破坏性变更、架构调整等场景，先补齐完整目标蓝图，再提案后实现。

## 工作流程

用户请求 → Phase Router → Task Analyzer → OpenSpec Gate（create-change）→ Proposal Agent → 完整目标蓝图 → 带蓝图追踪的 tasks

## 强制产物

- `proposal.md` 包含完整目标蓝图
- `tasks.md` 使用 Slice 拆分，并为每个 Slice 标明蓝图归属、依赖、后续项和验收
- 一次实现不完的内容必须保留未完成项和追踪位置
