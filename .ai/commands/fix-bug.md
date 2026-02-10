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

用于恢复既有行为的缺陷修复：默认跳过 OpenSpec 提案，直接进入实现与测试回归。

## 工作流程

用户请求 → Phase Router → Task Analyzer → OpenSpec Gate（通常 skip）→ Implement → Test → Session Summary
