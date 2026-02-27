# 模式：自动阶段路由（Phase Router）

> 从 ReleaseHub Phase Router Agent 实践提炼的可复用模式。

## 问题

长任务需要多阶段协作（分析 → 提案 → 设计 → 实现 → 测试 → 归档），但：
- 手动推进效率低，用户需要记住每个阶段的顺序和触发条件
- 不同任务类型需要跳过不同阶段，手动判断容易出错
- 阶段间的上下文传递容易遗漏

## 方案

引入 **Phase Router Agent**，作为中央路由器自动判断任务阶段并路由到对应 Agent。

```
用户请求
  ↓
Phase Router
  ├─→ 调用 Task Analyzer（分析任务类型）
  ├─→ 调用 Context Loader（加载上下文）
  ├─→ 调用 Experience Indexer（检索经验）
  ├─→ 调用 OpenSpec Gate（门禁决策）
  └─→ 路由到对应 Agent
       ├─→ Proposal Agent（提案阶段）
       ├─→ Design Agent（设计阶段）
       ├─→ Implement Agent（实现阶段）
       ├─→ Test Agent（测试阶段）
       └─→ Archive Agent（归档阶段）
```

## 核心机制

### 1. 任务类型识别

Phase Router 首先通过 Task Analyzer Skill 识别任务类型：

| 任务类型 | 识别信号 | 路由策略 |
|----------|---------|---------|
| 新功能 | "添加"、"实现"、"新增" | 检查 OpenSpec → 提案 → 设计 → 实现 → 测试 |
| Bug 修复 | "修复"、"解决"、"处理" | 跳过提案 → 实现 → 测试 |
| 重构 | "重构"、"优化"、"改进" | 跳过提案 → 实现 → 测试证明 |
| 查询 | "查看"、"显示"、"解释" | 直接回答，无阶段编排 |

### 2. 阶段跳过规则

不是所有任务都需要走完全部阶段。Phase Router 根据以下规则自动跳过：

| 条件 | 跳过阶段 |
|------|---------|
| `requiresOpenSpec = false` | 跳过 Proposal |
| 不涉及跨模块/迁移/安全/性能 | 跳过 Design |
| 已有充分测试覆盖 | 跳过 Test |
| 不是 OpenSpec change | 跳过 Archive |

### 3. 上下文传递

Phase Router 负责在阶段间传递上下文，确保下游 Agent 获得完整信息：

```
{
  task: string;          // 原始任务描述
  taskType: string;      // 识别出的任务类型
  contexts: object;      // 加载的上下文
  experiences: array;    // 检索到的经验
  gateDecision: object;  // 门禁决策结果
}
```

## 参考实现

ReleaseHub 的 Phase Router 实现位于 `.ai/agents/phase-router.md`，其核心流程：

1. 接收任务描述
2. 调用 `skill-task-analyzer` 分析任务类型
3. 调用 `skill-context-loader` 加载相关上下文
4. 调用 `skill-experience-indexer` 检索历史经验
5. 调用 `skill-openspec-gate` 进行门禁决策
6. 根据门禁结果路由到对应 Agent

## 可定制点

在其他项目中采用此模式时，以下部分可根据需要定制：

| 定制点 | 说明 | ReleaseHub 的选择 |
|--------|------|-------------------|
| 阶段列表 | 项目需要哪些阶段 | proposal → design → implement → test → archive |
| 跳过规则 | 哪些条件下跳过哪些阶段 | Bug 修复跳过 proposal，简单变更跳过 design |
| 门禁逻辑 | 什么任务需要走审批流程 | 新功能/破坏性变更/架构调整需要 OpenSpec |
| 上下文策略 | 如何加载和传递上下文 | 5 层加载（业务/技术/经验/会话/规范） |
| 经验检索 | 如何匹配和排序经验 | 关键词 40% + 领域 30% + 任务类型 30% |

## 适用场景

此模式适用于：
- 项目有多阶段开发流程（不只是"写代码"）
- 不同任务类型需要走不同流程
- 需要自动化判断和路由，减少人工干预

不适用于：
- 所有任务都走相同简单流程的项目
- 单人小项目，阶段管理无收益
