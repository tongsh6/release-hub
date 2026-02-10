# Skill: Context Loader

## 功能

根据任务描述和关键词，自动加载相关上下文文档。

## 输入

```typescript
{
  task: string;           // 任务描述
  keywords: string[];      // 关键词列表
  contextTypes: string[]; // 上下文类型：['business', 'tech', 'experience', 'spec']
}
```

## 输出

```typescript
{
  contexts: {
    business: string[];    // 业务上下文文件路径列表
    tech: string[];        // 技术上下文文件路径列表
    experience: string[];  // 经验文档路径列表
    spec: string[];        // 规范文档路径列表
  };
  summary: string;        // 上下文摘要
}
```

## 加载策略

### 1. 业务上下文（context/business/）
- 根据领域关键词匹配：`domain-model.md`, `user-stories-v1.3.md`
- 根据功能关键词匹配：相关用户故事片段

### 2. 技术上下文（context/tech/）
- 架构文档：`architecture/backend.md`
- API 文档：根据功能模块匹配
- 开发规范：根据任务类型匹配（backend/frontend/testing/database）

### 3. 历史经验（context/experience/）
- 通过 `.ai/summaries/experience-index.md` 检索
- 根据关键词匹配相关经验文档
- 优先加载相似度高的经验

### 4. 规范文档（openspec/）
- 检查是否有相关能力规范：`specs/{capability}/spec.md`
- 检查是否有活跃变更：`changes/{change-id}/`

## 示例

**输入**：
```json
{
  "task": "添加 Release Window 的冻结功能",
  "keywords": ["release-window", "freeze", "状态管理"],
  "contextTypes": ["business", "tech", "experience", "spec"]
}
```

**处理**：
1. 业务上下文：加载 `context/business/domain-model.md`（ReleaseWindow 相关部分）
2. 技术上下文：加载 `context/tech/api/release-window.md`
3. 历史经验：检索 experience-index，找到 `context/experience/lessons/state-management-pattern.md`
4. 规范文档：检查 `openspec/specs/release-window/spec.md`

**输出**：
```json
{
  "contexts": {
    "business": [
      "context/business/domain-model.md#release-window"
    ],
    "tech": [
      "context/tech/api/release-window.md",
      "context/tech/conventions/backend.md"
    ],
    "experience": [
      "context/experience/lessons/state-management-pattern.md"
    ],
    "spec": [
      "openspec/specs/release-window/spec.md"
    ]
  },
  "summary": "已加载 ReleaseWindow 领域模型、API 文档、状态管理模式经验、相关规范"
}
```

## 实现要点

1. **按需加载**：只加载与任务相关的部分，避免上下文窗口溢出
2. **优先级排序**：直接相关的文档优先，通用规范次之
3. **去重处理**：避免重复加载相同文档
4. **摘要生成**：为长文档生成摘要，只加载关键部分
