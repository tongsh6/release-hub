# Command: Fix Bug

## 用法

```
/fix-bug [问题描述]
```

或自然语言：

```
“修复 XXX 问题”
“XXX 接口返回不符合预期”
```

## 功能

用于恢复既有行为的缺陷修复：默认跳过 OpenSpec 提案，但必须先明确完整修复目标、影响范围、复现路径、回归验证和后续风险追踪。

## 工作流程

用户请求 → Phase Router → Task Analyzer → 完整修复目标 → OpenSpec Gate（通常 skip）→ Implement → Test → Static Scan TopN → Session Summary

## 强制要求

- 必须记录完整修复目标，而不是只修眼前报错
- 若发现更大范围缺陷但本次无法完成，必须保留后续项和追踪位置
- 代码变更后必须运行 `scripts/dev/static-scan-topn.sh 10` 或等价静态扫描命令
