# AIEF 扩展提案：ReleaseHub AI 工程化标准

> ReleaseHub 向 AIEF（AI Engineering Framework）贡献的三类标准规范和三个可复用模式。

## 摘要

本提案将 ReleaseHub 在实际开发中验证有效的 AI 工程化实践，提炼为 AIEF 标准扩展：

- **三类标准规范**：Skill 规范、Command 规范、Agent 规范
- **三个可复用模式**：自动阶段路由、三层经验管理、上下文自动加载

这些标准和模式已在 ReleaseHub 项目中经过实际验证（7 个 OpenSpec change 归档、12+ 经验条目、5 个标准化 Skill），具备直接被其他项目采用的成熟度。

---

## 背景

### 从 Speckit 到 AI 工程化

ReleaseHub 最初采用 Speckit 规范驱动方式，在实践中发现三个核心问题：

1. **上下文缺失**：AI 看不到历史经验、业务边界、配置规范
2. **知识不沉淀**：每次都从头开始，边际成本恒定
3. **范围太窄**：只管单个仓库，无法覆盖跨服务的复杂场景

为此演进出 AI 工程化三层架构（详见 `.ai/README.md`）：

- **上下文工程**：让 AI 自动获取完整信息
- **复合工程**：让每次实践都降低下次成本
- **多 Agent 协作**：长时间任务自动分解和推进

### 核心理念

> "从规范驱动到 AI 工程化"——不是让人适配工具，而是让工具适配人。

参考：[认知重建：Speckit 用了三个月，我放弃了](https://zhuanlan.zhihu.com/p/1993009461451831150)

---

## 贡献清单

| # | 贡献项 | 类型 | AIEF 建议级别 | 文件 |
|---|--------|------|--------------|------|
| 1 | Skill 规范标准 | 规范 | L1（推荐采纳） | [skill-spec.md](skill-spec.md) |
| 2 | Command 规范标准 | 规范 | L1（推荐采纳） | [command-spec.md](command-spec.md) |
| 3 | Agent 规范标准 | 规范 | L1（推荐采纳） | [agent-spec.md](agent-spec.md) |
| 4 | 自动阶段路由模式 | 模式 | L2（建议参考） | [patterns/phase-router.md](patterns/phase-router.md) |
| 5 | 三层经验管理模式 | 模式 | L2（建议参考） | [patterns/experience-management.md](patterns/experience-management.md) |
| 6 | 上下文自动加载模式 | 模式 | L2（建议参考） | [patterns/context-engineering.md](patterns/context-engineering.md) |

### AIEF 建议级别说明

| 级别 | 含义 | 适用范围 |
|------|------|---------|
| L0 | 基础对齐 | 所有项目必须遵循（如 REPO_SNAPSHOT 格式） |
| L1 | 推荐采纳 | 有 AI 工程化需求的项目应采纳的标准 |
| L2 | 建议参考 | 可根据项目规模和需求选择性采用的模式 |
| L3 | 实验性 | 仍在探索中的实践，仅供参考 |

---

## 与现有 AIEF 标准的关系

### L0：REPO_SNAPSHOT（已对齐）

ReleaseHub 已实现 `context/tech/REPO_SNAPSHOT.md`，与 AIEF L0 标准对齐。

### L1：三层架构（本次提案）

本次提案将 ReleaseHub 的三层 AI 架构（Agent → Command → Skill）标准化为 AIEF L1 规范：

```
┌─────────────────────────────────┐
│  Command（入口层）                │  ← 用户触发
│  定义"做什么"和"怎么触发"         │
├─────────────────────────────────┤
│  Agent（决策层）                  │  ← 系统编排
│  定义"怎么做"和"能力边界"         │
├─────────────────────────────────┤
│  Skill（执行层）                  │  ← 原子执行
│  定义"具体怎么执行"              │
└─────────────────────────────────┘
```

### L2：跨切面模式（本次提案）

三个可复用模式作为 L2 建议，项目可按需采用：

- **Phase Router**：自动阶段路由，适合有多阶段流程的项目
- **Experience Management**：三层经验管理，适合长生命周期项目
- **Context Engineering**：上下文自动加载，适合有丰富文档的项目

---

## ReleaseHub 实证数据

### 已验证的三层架构

| 层级 | 数量 | 具体项 |
|------|------|--------|
| Agent | 6 个 | Phase Router, Proposal, Design, Implement, Test, Archive |
| Command | 5 个 | implement-feature, fix-bug, refactor, plan-change, code-review |
| Skill | 5 个 | Task Analyzer, Context Loader, Experience Indexer, OpenSpec Gate, Session Summarizer |

### OpenSpec 实践

- **7 个 change 归档**：完整走过 提案 → 设计 → 实现 → 测试 → 归档 流程
- **OpenSpec 门禁**：通过 `skill-openspec-gate` 强制新功能必须先提案
- **规范一致性**：归档时自动校验 specs 与 changes 的一致性

### 经验管理

- **11 个经验类别**：覆盖状态管理、版本策略、DDD 架构、数据库迁移、API 设计、前端测试、发布流程、分支管理、迭代管理等
- **自动检索**：通过关键词匹配，任务前自动加载相关经验
- **持续沉淀**：每次任务完成后自动提取经验候选

### 上下文工程

- **5 层加载策略**：业务 → 技术 → 经验 → 会话 → 规范
- **自动匹配**：根据任务关键词和领域自动选择加载内容
- **零配置**：用户无需手动指定上下文

---

## 采用指南

### 最小采用（L1 规范）

1. 复制 `.ai/standards/` 目录到目标项目
2. 按 `skill-spec.md` 模板定义项目的 Skill
3. 按 `agent-spec.md` 模板定义项目的 Agent
4. 按 `command-spec.md` 模板定义用户入口

### 进阶采用（L2 模式）

在 L1 基础上，根据项目需要选择性采用：

| 模式 | 适用条件 | 预期收益 |
|------|---------|---------|
| Phase Router | 有多阶段开发流程 | 自动路由，减少人工判断 |
| Experience Management | 长生命周期项目 | 边际成本递减，避免重复踩坑 |
| Context Engineering | 有丰富文档/知识库 | 零配置上下文获取 |

---

## 目录结构

```
.ai/standards/
├── AIEF_EXTENSION_PROPOSAL.md    # 本文件：主提案
├── skill-spec.md                 # Skill 规范标准
├── command-spec.md               # Command 规范标准
├── agent-spec.md                 # Agent 规范标准
└── patterns/
    ├── phase-router.md           # 模式：自动阶段路由
    ├── experience-management.md  # 模式：三层经验管理
    └── context-engineering.md    # 模式：上下文自动加载
```

---

## 参考资源

- [.ai/README.md](../README.md) — ReleaseHub AI 工程化配置总规范
- [认知重建：Speckit 用了三个月，我放弃了](https://zhuanlan.zhihu.com/p/1993009461451831150) — 核心思想来源
- [上下文工程](https://context.engineering/) — 上下文工程方法论
- [复合工程](https://composition.engineering/) — 复合工程方法论
- [OpenSpec](https://openspec.dev/) — 规范驱动开发框架
