# AIEF Agent 规范标准

> 从 ReleaseHub 6 个 Agent 实践提炼的可复用标准。

## 什么是 Agent

Agent 是 AI 工程化架构中的**决策层角色**——具有明确职责、能力边界和决策逻辑的专用 AI 角色。

Agent 与 Skill 的核心区别：Agent 可以做决策（根据上下文选择行为），Skill 只执行确定性逻辑。

## 文件模板

```markdown
# Agent: {Name}

## 角色
[职责描述：这个 Agent 负责什么，一到两句话]

## 能力边界
- **能做**：[明确列出能力范围]
- **不能做**：[明确列出边界约束]

## 触发条件
[何时激活此 Agent：由 Phase Router 路由、由 Command 直接调用、或由其他 Agent 委托]

## 工作流程
[执行步骤——可选，当 Agent 有多步骤流程时提供]

## 依赖 Skills
[显式声明依赖的 Skill 列表]
```

## 命名约定

- 文件名：`{noun}.md`（Agent 以角色命名，不带动词前缀）
- 名词代表角色身份
- 示例：`phase-router.md`、`proposal.md`、`implement.md`

## 设计原则

| 原则 | 说明 | 反例 |
|------|------|------|
| 单一职责 | 一个 Agent 负责一个阶段或角色 | 一个 Agent 同时负责提案和实现 |
| 能力边界明确 | "不能做"与"能做"同样重要 | Agent 定义只写了"能做"，没有约束 |
| 依赖显式声明 | 所有使用的 Skill 必须在文档中声明 | Agent 隐式使用了未声明的 Skill |
| 可组合 | Agent 可被 Phase Router 编排组合 | Agent 硬编码了与其他 Agent 的交互 |
| 决策透明 | 决策逻辑可追溯、可解释 | Agent 的路由逻辑是黑盒 |

## 依赖声明规范

Agent 的 `依赖 Skills` 段必须列出所有直接依赖的 Skill：

```markdown
## 依赖 Skills

- `skill-context-loader` - 加载相关上下文
- `skill-experience-indexer` - 检索历史经验
- `skill-task-analyzer` - 分析任务类型和阶段
```

**规则**：
- 只声明直接依赖，不声明传递依赖
- 每个 Skill 后附简短用途说明
- 如果 Agent 不依赖任何 Skill，显式写 `无`

## Agent 类型分类

从 ReleaseHub 实践中归纳出两类 Agent：

### 编排型 Agent

负责任务路由和阶段管理，自身不执行具体任务：
- Phase Router：判断阶段、路由到对应 Agent

### 执行型 Agent

负责具体阶段的任务执行：
- Proposal：创建 OpenSpec 提案
- Design：补齐技术设计
- Implement：代码实现
- Test：测试补齐
- Archive：需求归档

## ReleaseHub 参考 Agent 简表

| Agent | 角色 | 类型 | 能力边界 | 依赖 Skills | 引用路径 |
|-------|------|------|---------|-------------|----------|
| Phase Router | 任务阶段路由 | 编排型 | 能：分析任务、路由 Agent、传递上下文；不能：执行具体任务 | context-loader, experience-indexer, task-analyzer, openspec-gate | `.ai/agents/phase-router.md` |
| Proposal | OpenSpec 提案 | 执行型 | 能：创建 change、编写 proposal/tasks/design；不能：未审批前进入实现 | task-analyzer, context-loader, experience-indexer, openspec-gate | `.ai/agents/proposal.md` |
| Design | 技术设计 | 执行型 | 能：补充决策、备选方案、风险回滚；不能：替代 Requirements | context-loader, experience-indexer | `.ai/agents/design.md` |
| Implement | 代码实现 | 执行型 | 能：实现任务、补测试、重构；不能：绕过 OpenSpec 门禁 | context-loader, openspec-gate | `.ai/agents/implement.md` |
| Test | 测试补齐 | 执行型 | 能：补覆盖、构建回归、修 flaky；不能：跳过测试或降低门禁 | context-loader | `.ai/agents/test.md` |
| Archive | 需求归档 | 执行型 | 能：归档 change、更新 specs、跑 validate；不能：替代发布流程 | context-loader | `.ai/agents/archive.md` |

## 与 Skill/Command 的三层关系

```
Command（用户入口）
  → 触发 Phase Router（编排型 Agent）
    → 路由到 执行型 Agent
      → 调用 Skill（原子技能）
```

- **Command** 定义"用户想做什么"
- **Agent** 定义"系统怎么做"
- **Skill** 定义"具体怎么执行"

## 何时创建新 Agent

满足**任一**条件时，应创建新 Agent：

1. **新阶段**：工作流中出现了新的阶段，现有 Agent 无法覆盖
2. **新角色**：需要一个具备独立决策能力的新角色
3. **职责混合**：现有 Agent 承担了过多职责，需要拆分

**不应该创建 Agent 的情况**：
- 只是确定性逻辑，不需要决策 → 应该是 Skill
- 只是现有 Agent 的参数变体 → 扩展现有 Agent 的触发条件
- 只被一个 Agent 内部使用的辅助逻辑 → 内联到 Agent 中
