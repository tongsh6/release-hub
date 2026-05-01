# ReleaseHub AI 工程化落地方案

> 归档自 `.trae/documents/ReleaseHub AI工程化落地方案.md`（Trae 会话产出）

## 目标与验收

- **提效目标**：需求/缺陷从"口头描述→可合并 PR"的端到端自动化比例提升
- **质量目标**：AI 产出必须可验证（测试通过、架构约束通过、OpenSpec 校验通过）
- **治理目标**：知识可复用（上下文自动加载、经验可检索、会话可续作）

## 现状盘点

- **已具备**：`context/` 知识库、`openspec/` 规范驱动、`.ai/` 三层架构骨架（agents/commands/skills）与经验索引雏形
- **主要缺口**：
  - Agent 引用未落地（proposal/design/implement/test）
  - `skill-task-analyzer` 未实现
  - 经验索引存在"待创建"条目
  - 经验索引存放位置需统一

## 方案总览（四条主线）

1. **上下文工程**：可控地把"对的文档"喂给"对的阶段"
2. **复合工程**：把每次任务沉淀成可复用 Skill/Agent/经验条目
3. **规范门禁**：OpenSpec + 代码/架构测试，形成"先规范后实现"的硬约束
4. **可观测与可审计**：记录工具调用轨迹、关键产物与验证结果，支持回溯

## 交付物清单

### .ai/skills（执行层）

- `skill-task-analyzer.md`：从用户描述抽取 taskType/domain/keywords/是否触发 OpenSpec
- `skill-openspec-gate.md`：实现"需要提案则禁止直接改代码"的门禁策略
- `skill-session-summarizer.md`：任务完成后生成会话摘要与沉淀清单

### .ai/agents（决策层）

- `proposal-agent.md`：创建/更新 OpenSpec change
- `design-agent.md`：必要时补 design.md
- `implement-agent.md`：按 tasks.md 顺序实现，强制 TDD
- `test-agent.md`：补齐单测/集成/e2e，确保回归覆盖
- `archive-agent.md`：发布后归档 change，更新 specs

### .ai/commands（入口层）

- 扩展命令：`/fix-bug`、`/refactor`、`/code-review`、`/plan-change`

## 关键设计决策

- **经验索引存放位置**：以 `.ai/summaries/experience-index.md` 为唯一真源
- **OpenSpec 触发策略**：新功能/破坏性变更 → 必须先 proposal；纯 bugfix/文档/测试 → 可直接实现
- **输出必须可验收**：每个 Agent 阶段都产出可审阅文件

## 实施步骤

1. 统一口径与最小治理
2. 补齐"经验→索引→可检索"闭环
3. 落地缺失的 Skill/Agent/Command
4. 质量门禁工程化
5. 演练与固化

## 风险与缓解

- Agent 误判是否需要 proposal → `skill-openspec-gate` 默认保守
- 上下文加载过多导致输出漂移 → Context Loader 强制优先级与摘要化
