# .ai/ - AI 工程化配置

> 基于上下文工程、复合工程、多 Agent 协作的 AI 自动化体系。参考：[认知重建：Speckit 用了三个月，我放弃了](https://zhuanlan.zhihu.com/p/1993009461451831150)

## 核心思想

### 从规范驱动到 AI 工程化

**问题**：规范驱动工具（speckit/openspec）在企业场景中遇到：
- 上下文缺失：AI 看不到历史经验、业务边界、配置规范
- 知识不沉淀：每次都从头开始，边际成本恒定
- 范围太窄：只管单个仓库，无法覆盖跨服务、跨系统的复杂场景

**解决方案**：
- **上下文工程**：让 AI 自动获取完整信息
- **复合工程**：让每次实践都降低下次成本
- **多 Agent 协作**：长时间任务自动分解和推进

---

## 目录结构

```
.ai/
├── README.md          # 本文件
├── agents/            # 🤖 Agent 定义（决策层）
├── commands/          # ⚡ 命令入口
├── skills/            # 🔧 Skill 定义（执行层）
├── reports/           # 📊 分析报告
│   ├── code-review/
│   ├── architecture/
│   └── performance/
├── proposals/         # 💡 方案建议
├── summaries/         # 📝 会话摘要/上下文
└── temp/              # 🗑️ 临时文档（已 gitignore）
```

---

## 三层 AI 架构

| 层级 | 目录 | 职责 | 设计原则 |
|------|------|------|----------|
| 决策层 | `agents/` | 定义 Agent 角色、能力边界、决策逻辑 | 单一职责，可组合 |
| 入口层 | `commands/` | 用户可调用的命令，触发 Agent 执行 | 自然语言触发，流程隐形化 |
| 执行层 | `skills/` | 原子化技能，被 Agent 组合调用 | 可复用，无状态 |

### 复利原则（复合工程）

每次 AI 执行任务后，必须考虑：
1. **Skill 沉淀**：是否有可复用的 Skill 可以沉淀？
2. **Agent 扩展**：是否需要新增 Agent 处理类似场景？
3. **经验记录**：经验是否应记录到 `context/experience/`？
4. **上下文索引**：是否需要在 `summaries/` 建立索引？

**目标**：边际成本递减，知识持续复利。

---

## 上下文工程（Context Engineering）

### 自动上下文加载策略

AI 在执行任务前，应自动加载相关上下文：

1. **业务上下文**：从 `context/business/` 加载领域模型、用户故事
2. **技术上下文**：从 `context/tech/` 加载架构、API、规范
3. **历史经验**：从 `context/experience/` 检索相关经验（见下方经验索引）
4. **会话上下文**：从 `.ai/summaries/` 加载当前会话摘要
5. **规范上下文**：从 `openspec/` 加载相关规范（如涉及变更）

### 经验索引（Experience Index）

在 `.ai/summaries/experience-index.md` 维护经验索引，格式：

```markdown
## 经验索引

### 问题类别：{category}
- **问题**：{问题描述}
- **解决方案**：{解决方案}
- **相关文件**：`context/experience/lessons/{file}.md`
- **标签**：{tag1}, {tag2}
```

AI 在执行任务时，应：
1. 根据任务关键词检索经验索引
2. 自动加载相关经验文档
3. 避免重复踩坑

### 上下文加载优先级

```
1. 当前任务直接相关的规范/文档（最高优先级）
2. 历史相似任务的经验（次高优先级）
3. 项目通用规范（默认加载）
4. 架构和设计文档（按需加载）
```

---

## 多 Agent 协作

### 长时间任务分解

对于复杂任务，使用多 Agent 协作模式：

```
用户请求 → Phase Router Agent → 分解任务
                ↓
    ┌───────────┼───────────┐
    ↓           ↓           ↓
Agent A    Agent B    Agent C
    ↓           ↓           ↓
    └───────────┼───────────┘
                ↓
        结果合并与验证
```

### Agent 协作模式

1. **顺序协作**：Agent A 完成后，Agent B 基于 A 的结果继续
2. **并行协作**：多个 Agent 同时处理独立子任务
3. **条件协作**：根据中间结果决定下一步 Agent

### Phase Router

`agents/phase-router.md` 负责：
- 判断当前任务处于哪个阶段
- 决定下一步应该调用哪个 Agent
- 管理任务状态和上下文传递

---

## Agent/Skill/Command 模板

### 定义新 Agent (`agents/{name}.md`)

```markdown
# Agent: {name}

## 角色
[Agent 的职责描述]

## 能力边界
- 能做：[...]
- 不能做：[...]

## 触发条件
[何时激活此 Agent]

## 依赖 Skills
- skill-a
- skill-b
```

### 定义新 Skill (`skills/{name}.md`)

```markdown
# Skill: {name}

## 功能
[Skill 的单一职责]

## 输入
[期望的输入格式]

## 输出
[产出的结果格式]

## 示例
[使用示例]
```

### 创建命令 (`commands/{name}.md`)

```markdown
# Command: {name}

## 用法
`/command-name [args]`

## 功能
[命令的作用]

## 调用的 Agent/Skill
- agent-a
- skill-b
```

---

## AI 生成文档

| 目录 | 用途 | 示例 |
|------|------|------|
| `reports/code-review/` | 代码审查、重构建议 | `2026-01-13-release-window-service.md` |
| `reports/architecture/` | 架构分析、依赖分析 | `2026-01-13-ddd-layer-analysis.md` |
| `reports/performance/` | 性能分析、优化建议 | `2026-01-13-api-performance.md` |
| `proposals/` | 功能方案、重构计划 | `2026-01-13-refactor-version-policy.md` |
| `summaries/` | 项目上下文、会话摘要 | `project-context.md` |
| `temp/` | 临时草稿、调试输出 | 不提交到 Git |

### 命名规范

- **带日期文档**：`YYYY-MM-DD-描述.md`
- **持久性文档**：`描述.md`（不带日期）

### Git 策略

- ✅ `agents/`、`commands/`、`skills/`、`reports/`、`proposals/`、`summaries/` - 提交到 Git
- ❌ `temp/` - 已添加到 `.gitignore`，不提交

---

## 流程隐形化

### 设计目标

让工具自然融入工作流，而非让人适配工具：

**过去**：
```
人："我要做一个需求"
AI："请先执行 Constitution → Specify → Plan → Tasks → Implement"
人：（手动执行每个阶段）
```

**现在**：
```
人："我要做一个需求"
AI：（自动判断阶段、加载上下文、执行任务、推进流程）
人：（只在关键决策点介入）
```

### 实现策略

1. **自然语言触发**：用户用自然语言描述需求，AI 自动识别意图
2. **自动阶段路由**：Phase Router 自动判断当前阶段和下一步
3. **上下文自动加载**：根据任务自动加载相关上下文，无需手动指定
4. **经验自动检索**：根据任务关键词自动检索历史经验
5. **状态自动保存**：任务状态自动保存，支持断点续做

### 隐形化进度

| 维度 | 当前状态 | 目标状态 |
|------|---------|---------|
| 命令入口 | ✅ 自然语言触发 | 完全自然对话 |
| 上下文加载 | 🔄 手动指定 | ✅ 自动检索加载 |
| 阶段流转 | 🔄 手动推进 | ✅ Agent 自主推进 |
| 经验沉淀 | 🔄 手动记录 | 🔄 自动识别并沉淀 |
| 跨会话连续性 | 🔄 依赖状态文件 | ✅ 无缝断点续做 |

---

## 最佳实践

### 1. 从真实场景出发

- 不要为了用工具而用工具
- 从高效的人的工作流程中提取模式
- 把高效流程 AI 化，而非套用理想化流程

### 2. 知识编码进工具

- 把经验编码成 Skill，而非只写文档
- 把规范编码成 Agent 决策逻辑，而非只写规范文档
- 把上下文编码成自动加载策略，而非只写索引

### 3. 追求边际成本递减

- 第一次做：建立 Skill/Agent/经验索引
- 第二次做：复用已有 Skill/Agent，成本降低
- 第 N 次做：完全自动化，成本接近零

### 4. 让工具适配人

- 工具应该"懂"我们的工作
- 工具应该"记得"我们的经验
- 工具应该"自然"融入日常

---

## 参考资源

- [认知重建：Speckit 用了三个月，我放弃了](https://zhuanlan.zhihu.com/p/1993009461451831150) - 本文核心思想来源
- [上下文工程](https://context.engineering/) - 上下文工程方法论
- [复合工程](https://composition.engineering/) - 复合工程方法论
- [OpenSpec](https://openspec.dev/) - 规范驱动开发框架
- [Anthropic 多 Agent 协作](https://www.anthropic.com/research/multi-agent-collaboration) - 多 Agent 协作架构
