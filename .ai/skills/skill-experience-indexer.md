# Skill: Experience Indexer

## 功能

根据任务关键词，从经验索引中检索相关历史经验。

## 输入

```typescript
{
  keywords: string[];      // 关键词列表
  taskType: string;        // 任务类型：'feature', 'bugfix', 'refactor', 'optimization'
  domain: string;          // 领域：'release-window', 'version-policy', etc.
}
```

## 输出

```typescript
{
  experiences: Array<{
    title: string;         // 经验标题
    category: string;      // 问题类别
    relevance: number;     // 相关度（0-1）
    filePath: string;      // 经验文档路径
    summary: string;       // 经验摘要
    tags: string[];        // 标签
  }>;
  recommendations: string[]; // 推荐行动
}
```

## 检索策略

### 1. 关键词匹配
- 精确匹配：关键词完全匹配
- 模糊匹配：关键词部分匹配
- 同义词匹配：使用同义词扩展

### 2. 领域匹配
- 优先返回同领域的经验
- 跨领域经验按相关度排序

### 3. 任务类型匹配
- 根据任务类型筛选相关经验
- 例如：bugfix → 优先返回错误处理经验

### 4. 相关度计算
```
相关度 = (关键词匹配度 * 0.4) + (领域匹配度 * 0.3) + (任务类型匹配度 * 0.3)
```

## 经验索引格式

经验索引文件：`.ai/summaries/experience-index.md`

```markdown
## 经验索引

### 问题类别：状态管理
- **问题**：如何实现 ReleaseWindow 的冻结/解冻功能
- **解决方案**：使用领域事件 + 状态机模式，避免直接修改状态
- **相关文件**：`context/experience/lessons/release-window-freeze-pattern.md`
- **标签**：`state-management`, `domain-event`, `release-window`
- **相关度关键词**：freeze, unfreeze, frozen, state

### 问题类别：版本策略
- **问题**：版本号格式验证的边界情况
- **解决方案**：使用策略模式，支持多种版本格式（SemVer, CalVer）
- **相关文件**：`context/experience/lessons/version-policy-validation.md`
- **标签**：`version-policy`, `validation`, `strategy-pattern`
- **相关度关键词**：version, policy, validation, format
```

## 示例

**输入**：
```json
{
  "keywords": ["freeze", "release-window", "状态"],
  "taskType": "feature",
  "domain": "release-window"
}
```

**处理**：
1. 关键词匹配：找到 "状态管理" 类别中的 freeze 相关经验
2. 领域匹配：release-window 领域匹配
3. 任务类型：feature 类型匹配
4. 相关度计算：0.9（高相关度）

**输出**：
```json
{
  "experiences": [
    {
      "title": "ReleaseWindow 冻结/解冻功能实现",
      "category": "状态管理",
      "relevance": 0.9,
      "filePath": "context/experience/lessons/release-window-freeze-pattern.md",
      "summary": "使用领域事件 + 状态机模式实现冻结功能，避免直接修改状态",
      "tags": ["state-management", "domain-event", "release-window"]
    }
  ],
  "recommendations": [
    "参考 release-window-freeze-pattern.md 中的状态机设计",
    "使用领域事件确保状态变更的一致性",
    "注意处理并发场景下的状态变更"
  ]
}
```

## 维护指南

### 添加新经验

1. 在 `context/experience/lessons/` 创建经验文档
2. 在 `.ai/summaries/experience-index.md` 添加索引条目
3. 确保包含足够的关键词和标签

### 更新经验索引

当发现经验文档更新时：
1. 检查索引条目是否仍准确
2. 更新相关度关键词
3. 添加新的标签
