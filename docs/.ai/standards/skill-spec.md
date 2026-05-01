# AIEF Skill 规范标准

> 从 ReleaseHub 5 个 Skill 实践提炼的可复用标准。

## 什么是 Skill

Skill 是 AI 工程化架构中的**执行层原子单元**——一个无状态、单一职责、有明确 I/O 契约的可复用技能。

Skill 不做决策，不编排流程，不维护状态。它接收输入，执行确定性逻辑，返回输出。

## 文件模板

```markdown
# Skill: {Name}

## 功能
[单一职责描述，一句话说清楚这个 Skill 做什么]

## 输入
\```typescript
{
  // TypeScript 接口定义，明确每个字段的类型和语义
}
\```

## 输出
\```typescript
{
  // TypeScript 接口定义，明确返回值结构
}
\```

## 执行策略
[具体算法、规则、判定逻辑——可选，当逻辑非平凡时必须提供]

## 示例
[完整的 Input → 处理过程 → Output 示例，至少一个]

## 边界约束
[明确声明不做什么——可选，当职责容易被误解时提供]
```

## 命名约定

- 文件名：`skill-{verb}-{noun}.md`
- 动词在前，名词在后，用连字符连接
- 示例：`skill-context-loader.md`、`skill-task-analyzer.md`

## 设计原则

| 原则 | 说明 | 反例 |
|------|------|------|
| 原子化 | 一个 Skill 只做一件事 | 一个 Skill 同时分析任务和加载上下文 |
| 无状态 | 相同输入必须产生相同输出 | Skill 依赖外部状态或会话上下文 |
| 可复用 | 可被多个 Agent 或 Command 组合调用 | Skill 硬编码了特定 Agent 的逻辑 |
| 明确 I/O | 输入输出用 TypeScript 接口定义 | 输入是"一段文字"，输出是"处理结果" |
| 可测试 | 给定输入可验证输出正确性 | 输出依赖不确定的外部环境 |

## 何时创建新 Skill

满足**全部**条件时，应创建新 Skill：

1. **可复用**：至少被 2 个 Agent 或 Command 引用，或预期会被复用
2. **原子化**：职责可用一句话描述清楚
3. **有明确契约**：输入输出可以用 TypeScript 接口定义
4. **无决策**：不需要根据上下文做分支判断（那是 Agent 的职责）

**不应该创建 Skill 的情况**：
- 逻辑只在一个地方使用且不太可能复用 → 内联到 Agent 中
- 需要做复杂决策或编排多个步骤 → 应该是 Agent
- 需要维护状态或会话上下文 → 应该是 Agent

## ReleaseHub 参考 Skill 简表

| Skill | 功能 | 输入 | 输出 | 引用路径 |
|-------|------|------|------|----------|
| Task Analyzer | 从任务描述提取类型、领域、关键词 | `{ task }` | `{ taskType, domain, keywords, phaseHint, requiresOpenSpec }` | `.ai/skills/skill-task-analyzer.md` |
| Context Loader | 按任务和关键词自动加载上下文 | `{ task, keywords, contextTypes }` | `{ contexts, summary }` | `.ai/skills/skill-context-loader.md` |
| Experience Indexer | 从经验索引检索相关历史经验 | `{ keywords, taskType, domain }` | `{ experiences[], recommendations[] }` | `.ai/skills/skill-experience-indexer.md` |
| OpenSpec Gate | 门禁决策：是否需要提案 | `{ task, taskType, requiresOpenSpec }` | `{ allowedNextPhase, action, reasons }` | `.ai/skills/skill-openspec-gate.md` |
| Session Summarizer | 生成会话摘要与经验候选 | `{ task, changeType, touchedAreas, ... }` | `{ summaryFilePath, experienceCandidates[] }` | `.ai/skills/skill-session-summarizer.md` |

## 与 Agent 的区别

| 维度 | Skill | Agent |
|------|-------|-------|
| 职责 | 执行确定性逻辑 | 做决策、编排流程 |
| 状态 | 无状态 | 可维护上下文 |
| 复用 | 被多个 Agent 共享 | 通常独立运行 |
| 粒度 | 原子操作 | 复合流程 |
| 触发 | 被 Agent 显式调用 | 被 Command 或 Phase Router 触发 |
