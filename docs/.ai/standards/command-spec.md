# AIEF Command 规范标准

> 从 ReleaseHub 5 个 Command 实践提炼的可复用标准。

## 什么是 Command

Command 是 AI 工程化架构中的**用户入口层**——用户通过 Command 触发 AI 工作流。Command 定义了触发方式、工作流阶段、调用链和产出物。

Command 是"面向用户"的，Agent 是"面向系统"的。用户只需要知道 Command，不需要理解底层 Agent 的编排逻辑。

## 文件模板

```markdown
# Command: {Name}

## 用法
[触发方式：斜杠命令 + 参数，以及等效的自然语言表达]

## 工作流程
[阶段列表，包含阶段间的流转条件和跳过规则]

## 调用链
[引用的 Agent 和 Skill，标明调用顺序和条件]

## 快速判断规则
[任务分类逻辑——可选，当 Command 需要根据输入走不同分支时提供]

## 产出物
[每个阶段的输出文件或状态变更]
```

## 命名约定

- 文件名：`{verb}-{noun}.md`
- 动词在前，名词在后，用连字符连接
- 示例：`implement-feature.md`、`fix-bug.md`、`code-review.md`

## 与 Agent 的区别

| 维度 | Command | Agent |
|------|---------|-------|
| 面向 | 用户（入口） | 系统（内部角色） |
| 职责 | 定义"做什么"和"怎么触发" | 定义"怎么做"和"能力边界" |
| 粒度 | 端到端流程 | 单阶段职责 |
| 可见性 | 用户可直接调用 | 用户通常不直接调用 |
| 编排 | 通过 Phase Router 编排多个 Agent | 自身执行或调用 Skill |

## 阶段编排模式

### 顺序编排

阶段严格按顺序执行：

```
Phase Router → Task Analyzer → Context Loader → Experience Indexer → Agent → Summary
```

### 条件跳过

根据任务类型跳过不需要的阶段：

```
Phase Router → Task Analyzer → OpenSpec Gate
  ├─ requiresOpenSpec = true → Proposal Agent → Design Agent → Implement → Test
  └─ requiresOpenSpec = false → Implement → Test
```

### 分支编排

根据任务类型走完全不同的流程：

```
Phase Router → Task Analyzer
  ├─ feature → 完整提案流程
  ├─ bugfix → 跳过提案，直接实现
  ├─ refactor → 跳过提案，实现 + 测试证明
  └─ review → 直接生成报告
```

## ReleaseHub 参考 Command 简表

| Command | 用法 | 工作流 | 关键特点 | 引用路径 |
|---------|------|--------|----------|----------|
| implement-feature | `/implement-feature [需求]` | Phase Router → 加载上下文 → 检索经验 → OpenSpec 检查 → 推进实现 | 完整新功能流程，自动经验沉淀 | `.ai/commands/implement-feature.md` |
| fix-bug | `/fix-bug [问题描述]` | Phase Router → Task Analyzer → OpenSpec Gate(skip) → Implement → Test | 跳过提案，直接修复 + 回归 | `.ai/commands/fix-bug.md` |
| refactor | `/refactor [重构目标]` | Phase Router → Task Analyzer → OpenSpec Gate(skip) → Implement → Test | 用测试证明行为不变 | `.ai/commands/refactor.md` |
| plan-change | `/plan-change [变更目标]` | Phase Router → Task Analyzer → OpenSpec Gate(create) → Proposal Agent | 显式创建 OpenSpec 提案 | `.ai/commands/plan-change.md` |
| code-review | `/code-review [范围]` | 直接生成审查报告 | 最简单的 Command，无阶段编排 | `.ai/commands/code-review.md` |

## 设计原则

| 原则 | 说明 |
|------|------|
| 自然语言等效 | 每个 Command 同时支持斜杠命令和自然语言触发 |
| 流程隐形化 | 用户只需描述意图，Command 自动编排流程 |
| 阶段可跳过 | 不需要的阶段自动跳过，而非强制用户走完整流程 |
| 产出物明确 | 每个阶段的输出有明确定义和存放位置 |

## 何时创建新 Command

满足**全部**条件时，应创建新 Command：

1. **用户可触发**：代表一个用户会主动发起的任务类型
2. **有标准流程**：任务有可重复的阶段编排
3. **区分度足够**：与现有 Command 的工作流有实质差异（不仅仅是参数不同）

**不应该创建 Command 的情况**：
- 只是现有 Command 的参数变体 → 在现有 Command 中增加参数
- 纯内部编排逻辑 → 应该是 Agent
- 一次性任务 → 直接用自然语言描述
