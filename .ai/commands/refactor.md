# Command: Refactor

## 用法

```
/refactor [重构目标]
```

## 功能

在不改变业务语义的前提下进行结构优化：先写完整重构蓝图，说明最终结构、分阶段路径、行为不变证明和未完成项追踪，再用测试证明行为不变。

## 工作流程

用户请求 → Phase Router → Task Analyzer → 完整重构蓝图 → OpenSpec Gate（通常 skip）→ Implement → Test → Static Scan TopN → Session Summary

## 强制要求

- 不允许只做局部清理而不说明最终重构目标
- 分阶段重构必须保留未完成项、依赖和追踪位置
- 代码变更后必须运行 `scripts/dev/static-scan-topn.sh 10` 或等价静态扫描命令
