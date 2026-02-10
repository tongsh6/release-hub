# AI 工程化实践指南

> 基于《认知重建：Speckit 用了三个月，我放弃了》的 AI 工程化思想

## 核心问题与解决方案

### 问题 1：上下文缺失

**现象**：
- AI 看不到历史经验、业务边界、配置规范
- 每次都要重新解释项目背景
- 上下文窗口频繁爆满

**解决方案：上下文工程（Context Engineering）**

1. **自动上下文加载**
   - 使用 `skill-context-loader` 根据任务自动加载相关文档
   - 优先级：直接相关 > 历史经验 > 通用规范

2. **经验索引机制**
   - 维护 `.ai/summaries/experience-index.md`
   - 使用 `skill-experience-indexer` 自动检索历史经验
   - 避免重复踩坑

3. **会话上下文管理**
   - 在 `.ai/summaries/` 保存会话摘要
   - 支持跨会话连续性

### 问题 2：知识不沉淀

**现象**：
- 每次都从头开始，边际成本恒定
- 好的实践停留在个人经验
- 团队无法复用

**解决方案：复合工程（Composition Engineering）**

1. **Skill 沉淀**
   - 每次任务后，提取可复用的 Skill
   - 记录到 `skills/` 目录
   - 下次类似任务直接复用

2. **Agent 扩展**
   - 识别重复场景，创建专用 Agent
   - 记录到 `agents/` 目录
   - 形成 Agent 组合能力

3. **经验记录**
   - 问题解决后，记录到 `context/experience/lessons/`
   - 更新 `.ai/summaries/experience-index.md`
   - 建立知识复利

### 问题 3：范围太窄

**现象**：
- 只管单个仓库，无法覆盖跨服务场景
- 流程过于理想化，不适应企业现实

**解决方案：多 Agent 协作 + 流程隐形化**

1. **多 Agent 协作**
   - 使用 `agent-phase-router` 分解长时间任务
   - Agent 之间协作，传递上下文
   - 支持顺序、并行、条件协作

2. **流程隐形化**
   - 用户用自然语言描述需求
   - AI 自动判断阶段、加载上下文、推进流程
   - 人只在关键决策点介入

## 实践步骤

### 第一步：建立经验索引

1. 创建 `.ai/summaries/experience-index.md`
2. 梳理已有经验，建立索引
3. 定义关键词和标签体系

**示例**：
```markdown
### 问题类别：状态管理
- **问题**：如何实现 ReleaseWindow 的冻结/解冻功能
- **解决方案**：使用领域事件 + 状态机模式
- **相关文件**：`context/experience/lessons/release-window-freeze-pattern.md`
- **标签**：`state-management`, `domain-event`, `release-window`
- **相关度关键词**：freeze, unfreeze, frozen, state
```

### 第二步：创建核心 Skill

1. **Context Loader**：自动加载相关上下文
2. **Experience Indexer**：检索历史经验
3. **Task Analyzer**：分析任务类型和阶段

**原则**：
- 单一职责
- 可复用
- 无状态

### 第三步：创建 Phase Router Agent

1. 定义阶段判断逻辑
2. 定义 Agent 路由规则
3. 管理上下文传递

**阶段定义**：
- 提案阶段：需要创建 openspec 提案
- 设计阶段：需要技术设计文档
- 实现阶段：编写代码
- 测试阶段：编写测试
- 完成阶段：归档和沉淀

### 第四步：创建命令入口

1. 定义自然语言触发规则
2. 调用 Phase Router
3. 管理任务生命周期

**示例命令**：
- `/implement-feature [需求描述]`
- `/fix-bug [问题描述]`
- `/refactor [重构目标]`

### 第五步：建立复利机制

每次任务完成后：

1. **提取 Skill**
   - 是否有可复用的操作模式？
   - 是否可以抽象成 Skill？

2. **扩展 Agent**
   - 是否有重复的场景？
   - 是否需要专用 Agent？

3. **记录经验**
   - 是否解决了新问题？
   - 是否发现了新模式？
   - 更新 experience-index.md

4. **更新上下文**
   - 是否有新的规范？
   - 是否有架构变更？
   - 更新相关文档

## 效果评估

### 边际成本递减

- **第一次**：建立 Skill/Agent/经验索引（成本高）
- **第二次**：复用已有 Skill/Agent（成本降低）
- **第 N 次**：完全自动化（成本接近零）

### 知识持续复利

- **个人**：经验沉淀，下次更快
- **团队**：知识共享，整体提效
- **项目**：上下文积累，理解成本降低

### 流程自然化

- **过去**：严格按流程执行，人适配工具
- **现在**：自然语言触发，工具适配人
- **未来**：完全隐形，工具消失

## 注意事项

### 1. 从真实场景出发

- 不要为了用工具而用工具
- 从高效的人的工作流程中提取模式
- 把高效流程 AI 化

### 2. 知识编码进工具

- 把经验编码成 Skill，而非只写文档
- 把规范编码成 Agent 决策逻辑
- 把上下文编码成自动加载策略

### 3. 追求边际成本递减

- 第一次做：建立可复用组件
- 第二次做：复用已有组件
- 第 N 次做：完全自动化

### 4. 让工具适配人

- 工具应该"懂"我们的工作
- 工具应该"记得"我们的经验
- 工具应该"自然"融入日常

## 参考资源

- [认知重建：Speckit 用了三个月，我放弃了](https://zhuanlan.zhihu.com/p/1993009461451831150)
- [上下文工程](https://context.engineering/)
- [复合工程](https://composition.engineering/)
- [OpenSpec](https://openspec.dev/)
- [Anthropic 多 Agent 协作](https://www.anthropic.com/research/multi-agent-collaboration)
