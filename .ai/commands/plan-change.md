# Command: Plan Change

## 用法

```
/plan-change [变更目标]
```

## 功能

显式创建 OpenSpec change：用于新功能、破坏性变更、架构调整等场景，先提案后实现。

## 工作流程

用户请求 → Phase Router → Task Analyzer → OpenSpec Gate（create-change）→ Proposal Agent
