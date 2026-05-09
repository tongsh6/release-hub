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
| [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md) | 项目事实台账（权威基线） | **每次会话首次加载**，判断项目阶段和已完成事项 |
| [context/](context/INDEX.md) | 项目知识库（长期记忆） | 理解业务/技术背景 |
| [context/experience/](context/experience/INDEX.md) | 经验索引（可检索） | 实现类任务前必读 |
| [requirements/](requirements/INDEX.md) | 需求管理 | 日常需求跟踪 |
| [openspec/](openspec/AGENTS.md) | 规范驱动开发 | 规划/提案/规范变更 |
| [context/tech/REPO_SNAPSHOT.md](context/tech/REPO_SNAPSHOT.md) | 仓库快照（快速理解） | 首次会话/快速定位 |
| [workflow/](workflow/INDEX.md) | 工作流阶段定义 | 理解流程/阶段路由 |
| [adr/](adr/README.md) | 架构决策记录（3 个 ADR） | 理解架构选型背景 |
| [reports/](reports/) | 验收报告（v0.1.9 / v0.1.10） | 了解验收状态 |
| [../scripts/](../scripts/README.md) | 脚本索引（验收/环境/扫描） | **验收前必读，不要手工逐 API 调试** |
| [../tasks/](../tasks/README.md) | 任务追踪（蓝图/切片/执行日志） | 理解当前推进计划 |
| [context/tech/architecture/ai-engineering-governance.md](context/tech/architecture/ai-engineering-governance.md) | AI 工程治理准则（长期演进原则） | **任何实现类任务前必读** |
| [.ai/standards/](.ai/standards/AIEF_EXTENSION_PROPOSAL.md) | AIEF 标准规范与可复用模式 | 扩展 AI 工程化框架 |
| [.ai-adapters/](.ai-adapters/README.md) | 多工具适配层 | 配置/对齐 AI 工具 |

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

### 工程治理准则（长期演进）

任何实现类任务（新功能、Bug 修复、重构、性能优化、架构调整）开始前，必须读取并遵循：

- [AI 工程治理准则](context/tech/architecture/ai-engineering-governance.md)

该准则将以下原则固化为项目级约束：

- **DRY**：消除业务规则重复，而不是表面去重
- **开闭原则**：新增变化点优先走 Port / Strategy / Registry
- **正交性**：生命周期、Git、版本、冲突、执行记录、UI 各自独立
- **切面化**：权限、审计、幂等、事务、锁、重试、指标统一处理
- **深模块**：窄接口、深实现，隐藏内部复杂性
- **复杂时序显式建模**：状态机、RunTask、依赖、执行记录
- **性能一等约束**：多仓库场景考虑并行、缓存、锁、聚合读模型
- **前沿建模范式**：DDD、状态机、领域事件、Process Manager、Policy、CQRS、DAG

AI 不得只完成眼前局部改动；必须评估是否破坏长期可维护性。

#### 完整规划 + 分步推进门禁

跨模块或复杂任务必须先形成完整目标蓝图，再拆成 DAG 和阶段任务线性推进。分步推进是执行策略，不得替代完整规划。

- **完整蓝图**：必须说明最终目标、完整范围、非目标、架构形态、数据/API/UI/测试影响、迁移与回滚策略。
- **阶段追踪**：即使一次实现不完，也必须把未完成 Slice、依赖关系、验收标准和后续顺序写入 `tasks.md`、需求、OpenSpec 或上下文文档。
- **事前约束**：每个 Slice 必须能回连到完整蓝图，并说明用户价值、端到端路径、单一目标、独立验收、可回滚边界、明确依赖和风险说明。
- **事后检查**：每个 Slice 完成后必须检查与完整蓝图的一致性，以及行为闭环、层级闭环、测试闭环、架构闭环、性能闭环、文档闭环和工作区闭环。
- 详细模板见 [AI 工程治理准则](context/tech/architecture/ai-engineering-governance.md)。

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
2. GREEN  → 写刚好满足当前测试且不偏离完整规划的实现
3. REFACTOR → 优化保持绿色
```

### 实现后静态扫码强制要求

任何 AI 工具完成代码实现后，最终交付前必须主动执行静态代码扫描，并保留扫描与 TopN 处理证据。

```bash
scripts/dev/static-scan-topn.sh 10
```

**强制流程**：

1. 扫描：运行统一脚本或等价静态扫描命令，生成 `.ai/reports/static-scan/<timestamp>/summary.md`
2. 排序：读取报告中的 TopN 问题清单，优先处理安全、错误、架构违规、可维护性高风险项
3. 修复：对 TopN 逐项修复；不能修复或不应修复的项必须记录原因
4. 复扫：修复后重新运行扫描或覆盖相关变更范围的静态扫描命令
5. 留痕：在报告中补充每项的处理方式、处理结果、复扫证据和未解决风险

最终回复必须包含静态扫描报告路径、TopN 处理结论和未解决风险；不得只说“已检查”而不保留证据。

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

# 实现后静态扫码 + TopN 留痕
scripts/dev/static-scan-topn.sh 10
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
| 任何实现类任务 | `context/tech/architecture/ai-engineering-governance.md` |
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

### 验收前强制检查（P0）

当 AI 需要验证项目功能是否正常工作时，**禁止手工逐 API 调试验收**。必须先检查并使用现成的验收脚本：

```bash
# 第一步：运行全链路验收（11 场景 / 25+ 验收项）
bash scripts/acceptance/run-acceptance.sh

# 第二步：查看验收报告
ls docs/reports/acceptance-*.md
```

手工逐 API 调试已知会踩的坑：
1. GitLab 种子仓库为空 → 编排 produce 0 items（需先运行 init-gitlab.sh）
2. GitLab Settings 未配置 → 部分 API 返回 500（需 POST /settings/gitlab）
3. 仓库 cloneUrl 与 GitLab 项目路径不匹配 → 分支操作 404
4. repo ID 来自过期数据库 → codeRepositoryPort.findById 返回空
5. 冲突检测/编排 API 依赖前置数据（versionInfo、featureBranch）→ 500

这些前置条件在 `run-acceptance.sh` 中已全部处理。绕过它的手工验证在 v0.1.10 验收中已浪费大量时间。详见 [scripts/README.md](../scripts/README.md)。

### 经验沉淀（任务完成后）

当发现有价值的经验时（踩坑、设计决策、最佳实践）：

1. 在 `context/experience/lessons/` 创建经验文档
2. 在 `context/experience/INDEX.md` 添加索引条目
3. 确保包含足够的关键词以便检索

---

## 上下文加载指南

| 场景 | 推荐加载 |
|------|----------|
| 任何实现类任务/长期演进判断 | `context/tech/architecture/ai-engineering-governance.md` |
| 快速了解项目 | `context/tech/REPO_SNAPSHOT.md` |
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
