# ReleaseHub 特性开发指导

> 基于 AI 工程化原则：上下文工程、复合工程、多 Agent 协作

## 🎯 核心原则

### 1. 上下文自动加载
- **无需手动指定**：AI 根据任务自动加载相关上下文
- **优先级明确**：直接相关 > 历史经验 > 通用规范
- **按需加载**：只加载必要部分，避免上下文窗口溢出

### 2. 知识持续沉淀
- **每次任务后**：提取可复用的 Skill、扩展 Agent、记录经验
- **边际成本递减**：第一次建立，第二次复用，第 N 次自动化
- **经验索引**：通过 `.ai/summaries/experience-index.md` 自动检索

### 3. 流程自然化
- **自然语言触发**：用自然语言描述需求，AI 自动判断阶段
- **自动阶段路由**：Phase Router 自动推进任务流程
- **关键决策点介入**：人只在需要决策时介入

### 4. 完整蓝图先行
- **先完整规划**：新功能、复杂修复、重构、架构调整必须先写完整目标蓝图
- **再分阶段推进**：一次实现不完可以，但未完成 Slice 必须保留在 tasks、需求、OpenSpec 或上下文中
- **可追踪交付**：每个 Slice 必须说明蓝图归属、依赖、后续项和验收方式

---

## 📋 开发流程

### 阶段概览

```
用户请求
  ↓
Phase Router（阶段路由）
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
  ├─→ 更新经验索引
  └─→ 保存会话摘要
```

### 详细阶段说明

#### 阶段 1：需求分析（自动）

**触发**：用户描述需求（自然语言或命令）

**AI 自动执行**：
1. **任务分析**（`skill-task-analyzer`）
   - 识别任务类型：新功能 / Bug 修复 / 重构
   - 提取关键词和领域概念
   - 判断复杂度
   - 判断是否需要完整目标蓝图或完整修复/重构蓝图

2. **上下文加载**（`skill-context-loader`）
   - 业务上下文：领域模型、用户故事
   - 技术上下文：架构、API、开发规范
   - 历史经验：通过经验索引检索
   - 规范文档：OpenSpec 相关规范

3. **经验检索**（`skill-experience-indexer`）
   - 根据关键词匹配历史经验
   - 加载相关经验文档
   - 避免重复踩坑

**输出**：
- 任务类型和阶段判断
- 相关上下文文档列表
- 历史经验摘要
- 完整目标蓝图的创建位置或复用位置

---

#### 阶段 2：OpenSpec 门禁（自动）

**触发**：任务分析完成后

**AI 自动执行**（`skill-openspec-gate`）：
1. 判断是否需要 OpenSpec 提案
   - 新功能 → 需要提案
   - Bug 修复 → 通常不需要
   - 重构 → 根据影响范围判断

2. 检查是否有现有 change
   - 有相关 change → 复用
   - 无相关 change → 创建新 change

**规则**：
- `requiresOpenSpec = true` 且无可复用 change → 创建提案
- `requiresOpenSpec = true` 且已有相关 change → 使用现有 change
- 否则 → 跳过 OpenSpec，直接实现

**输出**：
- 允许的下一阶段
- 建议的 action（create-change / use-existing-change / skip-openspec）

---

#### 阶段 3：提案创建（如需要）

**触发**：OpenSpec 门禁判断需要创建提案

**AI 自动执行**（`agent-proposal`）：
1. 创建 OpenSpec change 目录结构
2. 生成完整目标蓝图（可使用 `.ai/templates/complete-blueprint.md`）
3. 生成提案文档（`proposal.md`）
4. 创建带蓝图追踪的任务清单（`tasks.md`）
5. 更新需求文档（`requirements/in-progress/`）

**提案内容**：
- Why：为什么需要这个功能
- What Changes：具体变更内容
- Complete Target Blueprint：最终目标、完整范围、最终架构、阶段计划、验收矩阵、风险回滚
- Impact：影响范围
- 反向引用需求文档

**输出**：
- `openspec/changes/{change-id}/proposal.md`
- `openspec/changes/{change-id}/tasks.md`
- 需求文档更新

---

#### 阶段 4：技术设计（如需要）

**触发**：复杂功能或架构变更

**AI 自动执行**（`agent-design`）：
1. 分析技术方案
2. 创建设计文档（`design.md`）
3. 识别技术风险
4. 定义验收标准

**设计内容**：
- 架构设计
- 数据模型变更
- API 设计
- 前端组件设计

**输出**：
- `openspec/changes/{change-id}/design.md`

---

#### 阶段 5：代码实现

**触发**：提案/设计完成后，或直接实现（Bug 修复）

**AI 自动执行**（`agent-implement`）：
1. **后端实现**（如需要）
   - Domain 层：聚合根、实体、值对象
   - Application 层：用例编排、Port 接口
   - Infrastructure 层：JPA 实现、适配器
   - Interfaces 层：REST 控制器、DTO

2. **前端实现**（如需要）
   - API 类型生成：`pnpm gen:api`
   - 页面组件：Vue 3 Composition API
   - 状态管理：使用 `reactive`/`ref`，避免 Pinia

3. **数据库迁移**（如需要）
   - Flyway 迁移脚本：`V{数字}__{描述}.sql`
   - 遵循命名规范

**实现原则**：
- 遵循 DDD 分层架构约束
- 遵循 TDD：RED → GREEN → REFACTOR
- 遵循项目编码规范

**输出**：
- 代码实现
- 数据库迁移脚本（如需要）

---

#### 阶段 6：测试编写

**触发**：代码实现完成后

**AI 自动执行**（`agent-test`）：
1. **后端测试**
   - 单元测试：Domain 层逻辑
   - 集成测试：API 端点（MockMvc）
   - ArchUnit 测试：架构约束

2. **前端测试**
   - 单元测试：Vitest
   - E2E 测试：Playwright（可选）

**测试原则**：
- 覆盖关键路径
- 测试边界情况
- 保持测试可维护性

**输出**：
- 测试代码
- 测试报告

---

#### 阶段 7：知识沉淀（自动）

**触发**：任务完成后

**AI 自动执行**：
1. **提取 Skill**（如适用）
   - 识别可复用的操作模式
   - 创建新的 Skill 文档

2. **扩展 Agent**（如适用）
   - 识别重复场景
   - 创建专用 Agent

3. **记录经验**（如适用）
   - 创建经验文档：`context/experience/lessons/{name}.md`
   - 更新经验索引：`.ai/summaries/experience-index.md`

4. **更新上下文**（如适用）
   - 更新架构文档
   - 更新 API 文档
   - 更新开发规范

**输出**：
- 经验文档
- 经验索引更新
- Skill/Agent 扩展（如适用）

---

## 🚀 使用方式

### 方式 1：自然语言触发（推荐）

```
用户："我要添加一个版本回滚功能"
```

AI 自动：
1. 分析任务类型：新功能
2. 加载相关上下文：版本管理、ReleaseWindow、历史经验
3. 检查 OpenSpec：需要创建提案
4. 创建提案 → 实现代码 → 编写测试 → 沉淀经验

### 方式 2：命令触发

```
/implement-feature 添加 ReleaseWindow 的冻结功能
```

AI 自动执行完整流程。

### 方式 3：分阶段执行

```
用户："帮我创建版本回滚功能的提案"
→ AI 执行 Proposal Agent

用户："现在实现这个功能"
→ AI 执行 Implement Agent
```

---

## 📚 上下文加载策略

### 自动加载优先级

```
1. 当前任务直接相关的规范/文档（最高优先级）
   - openspec/specs/{capability}/spec.md
   - openspec/changes/{change-id}/
   - requirements/in-progress/{requirement}.md

2. 历史相似任务的经验（次高优先级）
   - .ai/summaries/experience-index.md（检索）
   - context/experience/lessons/{lesson}.md

3. 项目通用规范（默认加载）
   - AGENTS.md
   - context/tech/conventions/backend.md
   - context/tech/conventions/frontend.md

4. 架构和设计文档（按需加载）
   - context/tech/architecture/backend.md
   - context/business/domain-model.md
```

### 上下文类型

| 类型 | 目录 | 用途 |
|------|------|------|
| 业务上下文 | `context/business/` | 领域模型、用户故事、业务规则 |
| 技术上下文 | `context/tech/` | 架构、API、开发规范 |
| 历史经验 | `context/experience/` | 问题解决方案、最佳实践 |
| 规范文档 | `openspec/` | 功能规范、变更提案 |

---

## 🔧 关键 Skills 使用

### skill-context-loader

**功能**：根据任务自动加载相关上下文

**使用场景**：
- 任务开始时自动调用
- 需要补充上下文时手动调用

**示例**：
```typescript
{
  task: "添加 Release Window 的冻结功能",
  keywords: ["release-window", "freeze", "状态管理"],
  contextTypes: ["business", "tech", "experience", "spec"]
}
```

### skill-experience-indexer

**功能**：检索历史经验，避免重复踩坑

**使用场景**：
- 遇到类似问题时自动检索
- 需要参考历史解决方案时

**示例**：
- 关键词："状态管理" → 找到 `release-window-freeze-pattern.md`
- 关键词："版本号验证" → 找到 `version-policy-validation.md`

### skill-openspec-gate

**功能**：OpenSpec 门禁，确保规范驱动开发

**使用场景**：
- 新功能开发前检查是否需要提案
- 判断是否可以跳过 OpenSpec

**规则**：
- 新功能 → 需要提案
- Bug 修复 → 通常不需要
- 重构 → 根据影响范围判断

### skill-task-analyzer

**功能**：分析任务类型和阶段

**使用场景**：
- 任务开始时自动调用
- 需要判断任务类型时

**输出**：
- 任务类型：新功能 / Bug 修复 / 重构
- 当前阶段：提案 / 设计 / 实现 / 测试
- 下一步行动

---

## 📝 实际示例

### 示例 1：新功能开发

**用户输入**：
```
"我要添加一个版本回滚功能，允许用户回滚到之前的版本号"
```

**AI 执行流程**：

1. **Phase Router 分析**
   - 任务类型：新功能
   - 关键词：版本回滚、版本号、ReleaseWindow
   - 复杂度：中等

2. **Context Loader 加载**
   - 业务上下文：`context/business/domain-model.md`（版本管理部分）
   - 技术上下文：
     - `context/tech/api/release-window.md`
     - `context/tech/conventions/backend.md`
   - 历史经验：检索经验索引，找到版本管理相关经验
   - 规范文档：`openspec/specs/version-management/spec.md`

3. **OpenSpec Gate 检查**
   - 需要 OpenSpec：是
   - 现有 change：无
   - 行动：创建提案

4. **Proposal Agent 创建提案**
   - 创建 `openspec/changes/add-version-rollback/proposal.md`
   - 创建 `openspec/changes/add-version-rollback/tasks.md`
   - 更新 `requirements/in-progress/版本回滚功能.md`

5. **Implement Agent 实现**
   - Domain 层：添加 `VersionRollback` 领域服务
   - Application 层：添加 `VersionRollbackAppService`
   - Infrastructure 层：实现版本历史存储
   - Interfaces 层：添加 `POST /api/v1/release-windows/{id}/rollback` API
   - 前端：添加回滚按钮和确认对话框

6. **Test Agent 编写测试**
   - 单元测试：版本回滚逻辑
   - 集成测试：回滚 API
   - 前端测试：回滚 UI 交互

7. **知识沉淀**
   - 创建经验文档：`context/experience/lessons/version-rollback-pattern.md`
   - 更新经验索引：添加版本回滚相关条目

**输出**：
```
✅ 已创建 OpenSpec 提案：openspec/changes/add-version-rollback/
✅ 已实现版本回滚功能
✅ 已编写测试（单元测试 5 个，集成测试 3 个）
✅ 已更新经验索引
```

---

### 示例 2：Bug 修复

**用户输入**：
```
"修复 ReleaseWindow 列表分页时，第二页数据重复的问题"
```

**AI 执行流程**：

1. **Phase Router 分析**
   - 任务类型：Bug 修复
   - 关键词：分页、数据重复、ReleaseWindow
   - 复杂度：低

2. **Context Loader 加载**
   - 技术上下文：
     - `context/tech/api/release-window.md`
     - `openspec/changes/standardize-pagination/`（分页标准化变更）
   - 历史经验：检索分页相关经验

3. **OpenSpec Gate 检查**
   - 需要 OpenSpec：否（Bug 修复）
   - 行动：跳过 OpenSpec，直接实现

4. **Implement Agent 修复**
   - 检查分页查询逻辑
   - 修复偏移量计算错误
   - 确保 1-based page 正确转换

5. **Test Agent 编写测试**
   - 添加分页边界测试
   - 验证数据不重复

6. **知识沉淀**
   - 更新经验索引：添加分页问题相关条目

**输出**：
```
✅ 已修复分页数据重复问题
✅ 已添加分页边界测试
✅ 已更新经验索引
```

---

### 示例 3：重构

**用户输入**：
```
"重构 ReleaseWindowAppService，提取公共方法，保持行为不变"
```

**AI 执行流程**：

1. **Phase Router 分析**
   - 任务类型：重构
   - 关键词：重构、ReleaseWindowAppService、提取方法
   - 复杂度：低

2. **Context Loader 加载**
   - 技术上下文：
     - `context/tech/conventions/backend.md`
     - `context/tech/architecture/backend.md`
   - 历史经验：检索重构相关经验

3. **OpenSpec Gate 检查**
   - 需要 OpenSpec：否（不改变行为）
   - 行动：跳过 OpenSpec，直接重构

4. **Implement Agent 重构**
   - 提取公共方法
   - 保持测试通过
   - 遵循 TDD：RED → GREEN → REFACTOR

5. **Test Agent 验证**
   - 运行现有测试，确保全部通过
   - 不添加新测试（行为不变）

**输出**：
```
✅ 已重构 ReleaseWindowAppService
✅ 所有测试通过（无新增测试）
```

---

## 🎓 最佳实践

### 1. 从真实场景出发

- ✅ 不要为了用工具而用工具
- ✅ 从高效的工作流程中提取模式
- ✅ 把高效流程 AI 化

### 2. 知识编码进工具

- ✅ 把经验编码成 Skill，而非只写文档
- ✅ 把规范编码成 Agent 决策逻辑
- ✅ 把上下文编码成自动加载策略

### 3. 追求边际成本递减

- ✅ 第一次做：建立 Skill/Agent/经验索引
- ✅ 第二次做：复用已有 Skill/Agent
- ✅ 第 N 次做：完全自动化

### 4. 让工具适配人

- ✅ 工具应该"懂"我们的工作
- ✅ 工具应该"记得"我们的经验
- ✅ 工具应该"自然"融入日常

---

## 📖 相关文档

- [AI 工程化配置](.ai/README.md) - AI 工程化体系概览
- [AI 工程化实践指南](.ai/AI_ENGINEERING_GUIDE.md) - 详细实践步骤
- [项目上下文](.ai/summaries/project-context.md) - 项目技术栈和约束
- [经验索引](.ai/summaries/experience-index.md) - 历史经验检索
- [项目进度分析](../PROJECT_PROGRESS.md) - 当前项目状态

---

## 🔄 持续改进

### 每次任务后检查

1. **是否有可复用的 Skill？**
   - 识别重复操作模式
   - 创建新的 Skill 文档

2. **是否需要新的 Agent？**
   - 识别重复场景
   - 创建专用 Agent

3. **是否有新经验需要记录？**
   - 创建经验文档
   - 更新经验索引

4. **是否需要更新上下文？**
   - 更新架构文档
   - 更新 API 文档
   - 更新开发规范

### 定期回顾

- 每周回顾经验索引，合并相似经验
- 每月回顾 Skill/Agent，优化组合能力
- 每季度回顾上下文加载策略，优化优先级

---

*最后更新：2026-01-27*
