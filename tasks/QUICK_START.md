# ReleaseHub 任务系统快速决策指南

> 一页纸概览。新 AI 入场时从这里开始，不必同时读 5 个文件。

## 决策树

```
收到任务
  │
  ├── 纯查询/解释？
  │     └── 直接回答，不管任务系统
  │
  ├── Bug 修复 / 测试补齐 / 单文件小改？
  │     └── 直接实现，记录到 tasks/records/
  │
  ├── 需明确需求的新功能 / 破坏性变更？
  │     └── workflow: Proposal → Design → Implement → Review → Archive
  │               ↓
  │         tasks/: 用 blueprint-template.md 写蓝图
  │               ↓
  │         按垂直切片逐条实现，每条记录到 records/
  │
  └── 跨模块复杂重构 / 架构调整？
        └── workflow: Proposal → Design → Implement → Review → Archive
              (Design 阶段不可跳过)
```

## 两个体系如何协作

| 维度 | `workflow/` | `tasks/` |
|------|------------|----------|
| 定义什么 | **阶段流程**：什么时候做什么，产出什么 | **执行追踪**：具体怎么做，做到哪了 |
| 核心文件 | `phases/proposal.md` 等 5 个阶段 | `plans/` (计划) + `records/` (记录) |
| 模板 | 无（阶段文件本身即为规范） | `templates/blueprint-template.md` + `slice-log-template.md` |
| 类比 | CI/CD Pipeline 定义 | 每次 Run 的日志 |

**关系**：`workflow` 是流程定义，`tasks` 是每次执行该流程时的追踪记录。二者正交共存，不重复。

## 执行前必读（按优先级）

| 优先级 | 文件 | 理由 |
|--------|------|------|
| **必读** | `tasks/CONSTRAINTS.md` | 7 条硬约束，违反任何一条都是返工 |
| **必读** | `docs/AGENTS.md` | 项目上下文、自动加载规则 |
| **选读** | `docs/context/tech/architecture/ai-engineering-governance.md` | 深入理解 8 大原则（大型任务必读） |
| **参考** | `templates/blueprint-template.md` | 写蓝图时使用 |
| **参考** | `templates/slice-log-template.md` | 记录切片执行时使用 |

## 核心流程速查

### 1. 复杂任务 → 先写蓝图

用 `templates/blueprint-template.md` 写完整目标蓝图。蓝图必须包含：
- 最终行为、完整范围、非目标
- 架构形态、阶段计划、验收矩阵、风险与回滚

### 2. 蓝图 → 拆 DAG → 切片推进

```
确认设计
  → Slice 1: Domain + Test（无依赖）
  → Slice 2: Application + Test（依赖 Slice 1）
  → Slice 3: Infrastructure + API + Test（依赖 Slice 2）
  → Slice 4: Frontend（依赖 Slice 3）
  → Slice 5: E2E + Docs（依赖 Slice 4）
```

每个切片是贯穿 Domain→Application→Infrastructure→API→Frontend→Test 的端到端可交付单元。

### 3. 每个切片 → TDD + 事后检查

```
RED → GREEN → REFACTOR → VERIFY → 事后 7 项检查 → 记录到 records/
```

### 4. 所有代码完成 → 静态扫描

```bash
scripts/dev/static-scan-topn.sh 10
```

### 5. 审查通过 → 归档 + 沉淀经验

```
workflow: Review → Archive
          ↑ 检查静态扫描报告 + TopN 处理结论
```

## 关键禁止行为

| 禁止 | 原因 |
|------|------|
| 跨模块任务不写蓝图直接写代码 | 完整目标不清楚，返工率高 |
| 只做横向技术铺垫无用户可验证结果 | 不符合垂直切片要求 |
| 跳过静态扫描 | AI 工程治理准则强制要求 |
| 不记录切片执行日志 | 事后无法追溯做了什么 |
| 前端推导后端业务真相 | 违反正交性原则 |

## 目录结构速查

```
tasks/
├── QUICK_START.md        ← 本文件（你正在读）
├── CONSTRAINTS.md        ← 硬约束（必读）
├── README.md             ← 详细说明
├── templates/            ← 蓝图模板 + 切片日志模板
├── plans/                ← 当前阶段推进计划
└── records/              ← 历史执行日志

docs/workflow/phases/     ← 5 阶段流程定义
docs/openspec/            ← 规范驱动开发（API/架构变更）
docs/context/experience/  ← 历史经验（实现前检索）
```

## 首次使用时的检查清单

- [ ] 阅读 `tasks/CONSTRAINTS.md`
- [ ] 阅读 `docs/AGENTS.md`
- [ ] 了解 `tasks/templates/` 中模板的位置
- [ ] 知道 `scripts/dev/static-scan-topn.sh` 的存在
- [ ] 理解"垂直切片"的含义（端到端，不是横向铺垫）
