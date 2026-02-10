# Skill: Task Analyzer

## 功能

从用户任务描述中提取任务类型、领域、关键词与阶段提示，供 Phase Router 与门禁决策使用。

## 输入

```typescript
{
  task: string;
}
```

## 输出

```typescript
{
  taskType: 'feature' | 'bugfix' | 'refactor' | 'optimization' | 'docs' | 'chore';
  domain: string;
  keywords: string[];
  phaseHint: 'proposal' | 'design' | 'implement' | 'test' | 'archive';
  requiresOpenSpec: boolean;
  reasons: string[];
}
```

## 判定规则（建议）

- `bugfix`：描述以“修复/错误/异常/回归/不符合预期”且目标是恢复既有行为
- `feature`：新增能力/流程/接口/数据模型，或显式“添加/实现”
- `refactor`：目标是结构优化、命名调整、抽象提取，行为不变
- `optimization`：以性能/成本/延迟为目标的行为变化或实现替换
- `docs/chore`：文档/脚本/格式化/依赖升级等

## OpenSpec 触发（建议）

`requiresOpenSpec = true` 当且仅当满足任一条件：

- 新功能、破坏性变更、架构调整
- 安全、性能语义变化
- 数据模型或接口契约变化（含迁移）

## 示例

输入：

```json
{ "task": "新增 ReleaseWindow 冻结/解冻接口，并限制冻结后不可修改配置" }
```

输出：

```json
{
  "taskType": "feature",
  "domain": "release-window",
  "keywords": ["release-window", "freeze", "unfreeze", "接口", "状态"],
  "phaseHint": "proposal",
  "requiresOpenSpec": true,
  "reasons": ["新增接口与业务规则变化，需要先走 openspec 提案"]
}
```
