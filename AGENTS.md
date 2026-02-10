# ReleaseHub AI 编程指南

> 项目记忆入口，每次会话自动加载。详细规范按需从各子目录加载。

## 项目概述

**ReleaseHub** 是基于 DDD 的多仓库发布协调平台，管理 Release Windows、版本策略、分支规则和自动化版本更新。

**核心价值**：统一多仓库发布节奏管理，降低认知、协调和执行成本。

## 全局语言规则

- 交流内容必须使用中文；仅保留代码、命令、文件路径、标识符中的必要英文。

---

## 目录导航

| 目录 | 用途 | 何时加载 |
|------|------|----------|
| [context/](context/INDEX.md) | 项目知识库（长期记忆） | 理解业务/技术背景 |
| [context/experience/](context/experience/INDEX.md) | 经验索引（可检索） | 实现类任务前必读 |
| [requirements/](requirements/INDEX.md) | 需求管理 | 日常需求跟踪 |
| [openspec/](openspec/AGENTS.md) | 规范驱动开发 | 规划/提案/规范变更 |

### 知识库结构 (context/)

```
context/
├── business/      # 领域模型、项目规划、用户故事
├── tech/          # 架构、API、开发规范、服务分析
│   └── conventions/  # 后端/前端/测试/数据库规范
└── experience/    # 审计报告、踩坑记录
```

---

## 核心开发规范

### 架构约束 (DDD + 模块化单体)

```
releasehub-domain/        → 聚合根、实体、值对象（无外部依赖）
releasehub-application/   → 用例编排、事务边界（Port 接口）
releasehub-infrastructure/→ JPA 实现、适配器
releasehub-interfaces/    → REST 控制器、DTO
releasehub-bootstrap/     → Spring Boot 入口
```

**关键规则**：
- Domain 层禁止依赖其他层
- Application 层定义 Port 接口，Infrastructure 实现
- 领域模型使用工厂方法 (`createDraft()`) 和重建 (`rehydrate()`) 模式

### TDD 强制要求

```
1. RED    → 先写失败测试
2. GREEN  → 最小实现通过
3. REFACTOR → 优化保持绿色
```

### 前端规范 (Vue 3 + TypeScript)

- 页面状态：使用 `reactive`/`ref`，避免 Pinia
- API 类型：`pnpm gen:api` 自动生成
- 代理：`/api/*` → `http://localhost:8080`

---

## 快速命令

```bash
# 后端
cd release-hub && ./mvnw spring-boot:run -pl releasehub-bootstrap

# 前端
cd release-hub-web && pnpm dev

# 测试
mvn -q clean test
pnpm test
```

---

## 🔄 自动行为（AI 必读）

### 任务识别与路由

AI 在接收任务时，应自动判断任务类型并执行相应流程：

| 任务类型 | 识别信号 | 流程 |
|----------|---------|------|
| **新功能** | "添加"、"实现"、"新增" + 功能描述 | 检查 OpenSpec → 创建提案 → 设计 → 实现 → 测试 |
| **Bug 修复** | "修复"、"解决"、"处理" + 错误描述 | 加载经验 → 实现 → 测试 |
| **重构** | "重构"、"优化"、"改进" + 模块描述 | 评估影响 → 设计（如需）→ 实现 → 测试 |
| **查询** | "查看"、"显示"、"解释" | 直接回答 |

### 流程决策树

```
接收任务
  ↓
是否新功能/重大变更？
  ├─ 是 → 检查 openspec/changes/ 是否有相关提案
  │        ├─ 有 → 阅读提案，按 tasks.md 执行
  │        └─ 无 → 提示用户创建提案（或自动创建草稿）
  │
  └─ 否 → 加载上下文 → 直接执行
```

### 上下文自动加载

执行任务前，AI 应根据任务类型自动加载相关上下文（无需用户指定）：

| 任务涉及 | 自动加载 |
|----------|---------|
| 领域模型（ReleaseWindow/Iteration/VersionPolicy） | `context/business/domain-model.md` |
| 发布窗口 API | `context/tech/api/release-window.md` |
| 后端代码 | `context/tech/conventions/backend.md` |
| 前端代码 | `context/tech/conventions/frontend.md` |
| 测试 | `context/tech/conventions/testing.md` |
| 数据库迁移 | `context/tech/conventions/database.md` |
| 任何实现类任务 | `context/experience/INDEX.md`（检索相关经验）|

### 经验自动检索

执行实现类任务（新功能、Bug 修复、重构）前，**必须**：

1. 读取 `context/experience/INDEX.md`
2. 根据任务关键词匹配相关经验
3. 加载匹配的经验文档
4. 在执行前展示相关经验摘要

**触发条件**：

| 任务类型 | 关键词示例 | 自动检索 |
|----------|-----------|----------|
| 新功能 | 添加、实现、新增 | ✅ |
| Bug 修复 | 修复、解决、处理 | ✅ |
| 重构 | 重构、优化、改进 | ✅ |
| 纯查询 | 查看、列出、显示 | ❌ |

**示例**：

```
任务：添加 Release Window 冻结功能
↓ 自动检索 context/experience/INDEX.md
匹配关键词：freeze, 状态机, release-window
↓ 加载经验
context/experience/lessons/release-window-freeze-pattern.md
context/experience/lessons/release-window-lifecycle.md
↓ 展示摘要
"使用领域事件 + 状态机模式，冻结作为横切关注点"
↓ 执行任务（避免踩坑）
```

### 经验沉淀（任务完成后）

当发现有价值的经验时（踩坑、设计决策、最佳实践）：

1. 在 `context/experience/lessons/` 创建经验文档
2. 在 `context/experience/INDEX.md` 添加索引条目
3. 确保包含足够的关键词以便检索

---

## 上下文加载指南

| 场景 | 推荐加载 |
|------|----------|
| 理解业务需求 | `context/business/` |
| 技术实现决策 | `context/tech/architecture/` |
| API 开发 | `context/tech/api/` |
| 编写后端代码 | `context/tech/conventions/backend.md` |
| 编写前端代码 | `context/tech/conventions/frontend.md` |
| 编写测试 | `context/tech/conventions/testing.md` |
| 数据库迁移 | `context/tech/conventions/database.md` |
| 避免重复踩坑 | `context/experience/` |
| 规范/提案工作流 | `openspec/AGENTS.md` |
| AI 自动化扩展 | `.ai/README.md` |

---

<!-- OPENSPEC:START -->
## OpenSpec 规范驱动

当请求涉及以下内容时，加载 `@/openspec/AGENTS.md`：
- 提及规划或提案（proposal, spec, change, plan）
- 引入新功能、破坏性变更、架构调整
- 需要权威规范才能编码

<!-- OPENSPEC:END -->
