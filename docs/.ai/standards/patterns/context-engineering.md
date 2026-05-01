# 模式：上下文自动加载（Context Engineering）

> 从 ReleaseHub Context Loader Skill 实践提炼的可复用模式。

## 问题

手动指定上下文低效且易遗漏：
- 用户不确定该加载哪些文档
- 遗漏关键上下文导致 AI 产出质量低
- 每次任务都要重复指定相同的上下文
- 上下文过多导致窗口溢出，过少导致信息不足

## 方案

引入 **Context Loader Skill**，按任务类型自动加载相关上下文，实现"零配置"的上下文获取。

```
任务描述
  ↓
Task Analyzer（提取关键词和领域）
  ↓
Context Loader（按策略加载）
  ├─→ 业务上下文（领域模型、用户故事）
  ├─→ 技术上下文（架构、API、规范）
  ├─→ 历史经验（匹配的经验文档）
  ├─→ 会话上下文（当前会话摘要）
  └─→ 规范上下文（相关规范文档）
  ↓
上下文摘要（去重、排序、摘要化）
```

## 五层加载策略

### 1. 业务上下文（最高优先级）

从项目业务知识库加载：
- 领域模型（聚合根、实体、值对象的定义和关系）
- 用户故事（功能需求的业务描述）

**加载条件**：任务涉及业务概念时自动加载

### 2. 技术上下文

从技术文档库加载：
- 架构文档（分层结构、模块划分）
- API 文档（接口定义、请求/响应格式）
- 开发规范（编码规范、测试规范、数据库规范）

**加载条件**：根据任务涉及的技术栈自动匹配

### 3. 历史经验

从经验索引检索相关经验：
- 通过 Experience Indexer 按关键词匹配
- 优先加载相似度高的经验文档

**加载条件**：所有实现类任务（新功能、Bug 修复、重构）

### 4. 会话上下文

从会话摘要加载：
- 当前会话的历史操作记录
- 项目基础上下文

**加载条件**：需要连续性的任务（跨会话续做）

### 5. 规范上下文

从规范文档加载：
- 能力规范（spec.md）
- 活跃变更（changes/）

**加载条件**：涉及规范驱动开发的任务

## 优先级排序

```
1. 当前任务直接相关的规范/文档（最高优先级）
2. 历史相似任务的经验（次高优先级）
3. 项目通用规范（默认加载）
4. 架构和设计文档（按需加载）
```

**原则**：宁可多加载一些相关上下文，也不要遗漏关键信息。但要控制总量，避免窗口溢出。

## 去重与摘要化

### 去重处理

同一文档被多个策略命中时只加载一次：

```
业务上下文命中 domain-model.md
技术上下文也命中 domain-model.md
→ 去重后只加载一次
```

### 摘要化

对于长文档，只加载与任务相关的部分：

```
domain-model.md 有 500 行
任务只涉及 ReleaseWindow
→ 只加载 ReleaseWindow 相关的 50 行
```

## 参考实现

ReleaseHub 的上下文加载实现：

| 组件 | 路径 | 职责 |
|------|------|------|
| Context Loader Skill | `.ai/skills/skill-context-loader.md` | 按策略加载上下文 |
| Task Analyzer Skill | `.ai/skills/skill-task-analyzer.md` | 提取关键词和领域（上游输入） |
| 业务知识库 | `context/business/` | 领域模型、用户故事 |
| 技术文档 | `context/tech/` | 架构、API、规范 |
| 经验索引 | `.ai/summaries/experience-index.md` | 经验检索入口 |
| 会话摘要 | `.ai/summaries/` | 会话上下文 |

### ReleaseHub 的加载映射表

| 任务涉及 | 自动加载 |
|----------|---------|
| 领域模型（ReleaseWindow/Iteration/VersionPolicy） | `context/business/domain-model.md` |
| 发布窗口 API | `context/tech/api/release-window.md` |
| 后端代码 | `context/tech/conventions/backend.md` |
| 前端代码 | `context/tech/conventions/frontend.md` |
| 测试 | `context/tech/conventions/testing.md` |
| 数据库迁移 | `context/tech/conventions/database.md` |
| 任何实现类任务 | `context/experience/INDEX.md` |

## 可定制点

| 定制点 | 说明 | ReleaseHub 的选择 |
|--------|------|-------------------|
| 加载层数 | 项目需要几层上下文 | 5 层（业务/技术/经验/会话/规范） |
| 知识库结构 | 文档的目录组织方式 | `context/{business,tech,experience}/` |
| 匹配策略 | 如何将任务映射到文档 | 关键词 + 领域 + 任务类型 |
| 摘要策略 | 长文档如何截取 | 按锚点（#section）加载相关部分 |
| 优先级 | 上下文的加载顺序 | 直接相关 > 经验 > 通用规范 > 架构 |

## 适用场景

此模式适用于：
- 项目有丰富的文档和知识库
- AI 需要根据不同任务加载不同上下文
- 手动指定上下文效率低且容易遗漏

不适用于：
- 文档极少的项目（没有上下文可加载）
- 所有任务都需要相同上下文的项目（直接全量加载即可）
