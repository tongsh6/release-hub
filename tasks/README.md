# ReleaseHub 任务追踪中心

> 项目级 AI 任务执行追踪基础设施。将 [AI 工程治理准则](../docs/context/tech/architecture/ai-engineering-governance.md) 中的「完整蓝图 → DAG 拆分 → 垂直切片推进 → 事后检查 → 静态扫描留痕」流程固化为可操作的任务管理文件。

## 与现有体系的关系

| 体系 | 用途 | 本目录关系 |
|------|------|-----------|
| `docs/workflow/` | 定义 5 个阶段流程规范（提案→设计→实现→审查→归档） | tasks 消费 workflow 的阶段定义来规划任务批次 |
| `docs/openspec/` | 规范驱动开发，管理 API/架构变更提案 | openspec 定义 What（规格），tasks 定义 How（执行计划） |
| `docs/requirements/` | 日常需求跟踪 | requirements 是入口，tasks 是执行追踪 |
| `docs/context/experience/` | 经验沉淀与检索 | 任务结束后向 experience 贡献经验 |

## 目录结构

```
tasks/
├── README.md                           # 本文件
├── CONSTRAINTS.md                      # 任务规划硬约束（必读）
├── templates/
│   ├── blueprint-template.md           # 完整目标蓝图模板
│   └── slice-log-template.md           # 垂直切片执行日志模板
├── plans/                              # 推进计划（按日期/阶段命名）
│   └── YYYY-MM-DD-phase-N.md
└── records/                               # 执行日志（按切片记录）
    └── YYYY-MM-DD-slice-N-log.md
```

## 使用方式

### 每次 AI 推进任务前

1. 读取 `CONSTRAINTS.md`，确认任务规划符合硬约束
2. 检查 `plans/` 中当前阶段的推进计划
3. 如果是新的复杂任务：使用 `templates/blueprint-template.md` 创建完整目标蓝图
4. 将蓝图拆分为垂直切片，每个切片使用 `templates/slice-log-template.md` 记录

### 每次 AI 推进任务后

1. 更新对应的切片日志（事后检查 7 项）
2. 更新推进计划中的完成状态
3. 检查是否需要向 `docs/context/experience/lessons/` 沉淀经验
4. 检查是否需要更新 `docs/context/tech/REPO_SNAPSHOT.md`

### 简单任务

Bug 修复、文档调整、测试补齐等不需要完整蓝图的简单任务，直接在 `records/` 中记录执行摘要。

## 核心概念

- **完整目标蓝图**：跨模块任务的整体设计文档，包含最终行为、完整范围、非目标、架构形态、阶段计划、验收矩阵、风险与回滚
- **垂直切片**：贯穿 Domain→Application→Infrastructure→API→Frontend→Test 必要层的端到端可交付单元
- **DAG**：切片间的有向无环依赖图，标注并行/串行关系

## 快速入口

- **新 AI 入场必读** → [QUICK_START.md](QUICK_START.md) — 一页纸决策指南
- 想了解任务规划约束 → [CONSTRAINTS.md](CONSTRAINTS.md)
- 想创建新的推进计划 → [templates/blueprint-template.md](templates/blueprint-template.md)
- 想记录切片执行日志 → [templates/slice-log-template.md](templates/slice-log-template.md)
- 想查看当前推进计划 → [plans/](plans/)
- 想查看历史执行记录 → [records/](records/)
