# Command: Implement Feature

## 用法

```
/implement-feature [需求描述]
```

或自然语言：
```
"我要实现一个 XXX 功能"
"帮我添加 XXX 特性"
```

## 功能

实现新功能的完整流程，包括：
1. 自动加载相关上下文
2. 检索历史经验
3. 检查是否需要创建 openspec 提案
4. 按阶段推进实现
5. 自动沉淀经验

## 工作流程

```
用户请求
  ↓
Phase Router Agent
  ├─→ 分析任务类型和阶段
  ├─→ 加载相关上下文（Context Loader）
  ├─→ 检索历史经验（Experience Indexer）
  └─→ 路由到对应 Agent
       ├─→ 提案阶段 → Proposal Agent
       ├─→ 设计阶段 → Design Agent
       ├─→ 实现阶段 → Implement Agent
       └─→ 测试阶段 → Test Agent
  ↓
任务完成
  ├─→ 更新经验索引（`.ai/summaries/experience-index.md`）
  └─→ 保存会话摘要
```

## 调用的 Agent/Skill

- `agent-phase-router` - 阶段路由
- `skill-context-loader` - 上下文加载
- `skill-experience-indexer` - 经验检索
- `agent-proposal` - 提案创建（如需要）
- `agent-implement` - 代码实现
- `agent-test` - 测试编写

## 示例

**输入**：
```
/implement-feature 添加 ReleaseWindow 的冻结功能
```

**处理流程**：
1. Phase Router 分析：新功能 → 需要提案
2. Context Loader 加载：
   - `context/business/domain-model.md`（ReleaseWindow 部分）
   - `context/tech/api/release-window.md`
   - `context/tech/conventions/backend.md`
3. Experience Indexer 检索：
   - 找到状态管理相关经验
4. 检查 openspec：
   - 无相关提案 → 创建提案
5. 推进实现：
   - 创建 openspec 提案
   - 实现代码
   - 编写测试
6. 沉淀经验：
   - 更新 `.ai/summaries/experience-index.md`

**输出**：
```
✅ 已创建 openspec 提案：changes/add-release-window-freeze/
✅ 已实现冻结功能
✅ 已编写测试
✅ 已更新经验索引
```

## 特性

- **自动上下文加载**：无需手动指定，自动加载相关文档
- **经验自动检索**：自动检索历史经验，避免重复踩坑
- **流程自动推进**：自动判断阶段，推进任务
- **经验自动沉淀**：任务完成后自动更新经验索引
