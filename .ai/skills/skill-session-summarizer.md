# Skill: Session Summarizer

## 功能

在任务完成后生成会话摘要，记录关键决策、变更点、验证方式与可沉淀经验，保存到 `.ai/summaries/`。

## 输入

```typescript
{
  task: string;
  changeType: string;
  touchedAreas: string[];
  changedFiles: string[];
  testsRun: string[];
  keyDecisions: string[];
  followUps: string[];
}
```

## 输出

```typescript
{
  summaryFilePath: string;
  experienceCandidates: Array<{
    title: string;
    suggestedFilePath: string;
    keywords: string[];
  }>;
}
```

## 输出格式（建议）

- 文件命名：`YYYY-MM-DD-<topic>.md`
- 内容包含：目标、变更摘要、关键文件、验证结果、风险/回滚、经验沉淀建议
