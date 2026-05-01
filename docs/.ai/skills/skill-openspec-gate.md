# Skill: OpenSpec Gate

## 功能

对任务执行进行 OpenSpec 门禁：当任务需要提案时，禁止直接进入实现阶段，先创建或更新 change。

## 输入

```typescript
{
  task: string;
  taskType: string;
  requiresOpenSpec: boolean;
  existingChangeIds?: string[];
}
```

## 输出

```typescript
{
  allowedNextPhase: 'proposal' | 'design' | 'implement' | 'test';
  action: 'create-change' | 'use-existing-change' | 'skip-openspec';
  suggestedChangeId?: string;
  reasons: string[];
}
```

## 规则（建议）

- `requiresOpenSpec = true` 且无可复用 change ⇒ `allowedNextPhase = proposal`，`action = create-change`
- `requiresOpenSpec = true` 且已有相关 change ⇒ `allowedNextPhase = proposal`，`action = use-existing-change`
- 否则 ⇒ `allowedNextPhase = implement`，`action = skip-openspec`

## 示例

输入：

```json
{
  "task": "重构 ReleaseWindowAppService 的方法命名，保持行为不变",
  "taskType": "refactor",
  "requiresOpenSpec": false
}
```

输出：

```json
{
  "allowedNextPhase": "implement",
  "action": "skip-openspec",
  "reasons": ["重构且不改变行为，可直接实现并用测试守护"]
}
```
