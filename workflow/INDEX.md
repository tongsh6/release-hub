# 工作流阶段定义

> 定义 ReleaseHub 的端到端工作流阶段，从提案到归档。

## 与 `.ai/agents/` 的关系

- **`workflow/phases/`** 定义**阶段流程规范**：做什么、何时做、产出什么
- **`.ai/agents/`** 定义**角色与能力**：谁来做、怎么做、依赖哪些 Skill
- 两者**正交共存**：workflow 引用 agent，agent 不反向依赖 workflow

## 阶段总览

```
提案 (Proposal)  →  设计 (Design)  →  实现 (Implement)  →  审查 (Review)  →  归档 (Archive)
     ↑                  ↑                                        │
     │              可跳过                                       │
     └──────────── 驳回返工 ──────────────────────────────────────┘
```

| 阶段 | 文件 | 执行 Agent | 触发信号 |
|------|------|-----------|---------|
| 提案 | [phases/proposal.md](phases/proposal.md) | `.ai/agents/proposal.md` | 新功能、破坏性变更、架构调整 |
| 设计 | [phases/design.md](phases/design.md) | `.ai/agents/design.md` | 跨模块、迁移、安全/性能敏感 |
| 实现 | [phases/implement.md](phases/implement.md) | `.ai/agents/implement.md` | tasks.md 就绪 |
| 审查 | [phases/review.md](phases/review.md) | `.ai/agents/test.md` + `/code-review` | 代码变更完成 |
| 归档 | [phases/archive.md](phases/archive.md) | `.ai/agents/archive.md` | 审查通过、合并/部署完成 |

## 快速决策：何时可跳过阶段

| 任务类型 | 跳过的阶段 | 原因 |
|---------|-----------|------|
| 纯 Bug 修复（恢复既有行为） | 提案、设计 | 不引入新行为 |
| 文档/格式调整 | 提案、设计、审查 | 无代码变更 |
| 测试补齐 | 提案、设计 | 不改变业务逻辑 |
| 重构（不改接口） | 提案 | 内部优化，需审查 |

## 工程治理门禁

所有实现类任务进入阶段流转前，必须先读取并遵循：

- [AI 工程治理准则](../context/tech/architecture/ai-engineering-governance.md)

阶段执行时必须先写完整目标蓝图，再把大任务拆成 DAG，并按垂直切片线性分批。分批是推进方式，不得把完整目标缩成“本次最小闭环”。若任务涉及复杂时序、跨模块副作用、性能敏感路径或长期架构方向，设计阶段不得跳过。

实现阶段完成代码后，必须执行 `scripts/dev/static-scan-topn.sh 10` 或等价静态扫描命令；审查阶段必须检查 `.ai/reports/static-scan/` 中的扫描结果、TopN 处理方式、处理结果和复扫证据。

## 相关文档

- [AGENTS.md](../AGENTS.md) — 项目记忆入口
- [.ai/agents/](../.ai/agents/) — Agent 角色定义
- [openspec/AGENTS.md](../openspec/AGENTS.md) — 规范驱动工作流
- [context/experience/INDEX.md](../context/experience/INDEX.md) — 经验索引
