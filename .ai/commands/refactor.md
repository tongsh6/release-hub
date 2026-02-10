# Command: Refactor

## 用法

```
/refactor [重构目标]
```

## 功能

在不改变业务语义的前提下进行结构优化：要求用测试证明行为不变，并在必要时补充设计说明。

## 工作流程

用户请求 → Phase Router → Task Analyzer → OpenSpec Gate（通常 skip）→ Implement → Test → Session Summary
