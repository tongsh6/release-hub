# ReleaseHub 项目总体规划书

## 1. 背景与问题定义
ReleaseHub 面向「多项目、多仓库」场景，统一管理发布窗口（Release Window）、版本号演进、分支策略与子项目结构，解决团队在多仓协作中常见的三类成本：
- **认知成本**：版本、分支、窗口分散在各仓库/文档中，信息不可追溯。
- **协调成本**：发布窗口冲突、跨仓版本不一致、变更传播慢。
- **执行成本**：版本号更新与校验依赖人工，易错且难规模化。

目标是做成一个“发布节奏的单一事实源（Single Source of Truth）”，并为未来的 CI/CD、ChatOps、权限与通知扩展预留接口。

---

## 2. 项目目标与成功标准（Success Criteria）

### 2.1 核心目标（MVP）
1) **发布窗口统一规划与展示**
- 可视化展示不同项目/仓库的发布窗口日历/列表
- 支持窗口状态流转与追踪

2) **多仓发布状态与分支/版本规范统一**
- 统一配置分支命名规则（BranchRule）
- 统一配置版本策略（VersionPolicy）

3) **版本号自动管理（Maven/Gradle）**
- Maven：支持自动更新 `pom.xml` 版本（配合 Flatten Plugin 约束）
- Gradle：支持更新 `gradle.properties` / `build.gradle` 中版本（第一期先做轻量实现）

### 2.2 交付标准（客观可验收）
- **信息一致性**：同一发布窗口的“目标版本、关联仓库、状态”可追踪且有审计记录（至少 createdBy/createdAt/updatedAt）。
- **可操作性**：从 UI 创建 Release Window → 绑定仓库/子项目 → 一键执行版本更新 → 可看到执行结果与差异。
- **可扩展性**：核心策略（分支规则/版本策略/更新器）可插拔，支持后续扩展到 CI、通知、权限。

---

## 3. 术语与领域语言（Ubiquitous Language）
- **ReleaseWindow**：一次发布窗口，具有起止时间、目标版本、状态、影响范围（多个仓库/子项目）。
- **Project**：逻辑项目（可能对应多个仓库）。
- **Repository**：代码仓库实体（Git repo）。
- **SubProject**：子项目（Maven module/Gradle subproject）。
- **BranchRule**：分支命名与校验规则（例如 `release/{version}`、`hotfix/{issue}`）。
- **VersionPolicy**：版本策略（SemVer、日期版本、带里程碑后缀等）。
- **VersionUpdater**：版本更新器（pom/gradle 的写回、diff、校验）。

---

## 4. 范围边界（Scope）

### 4.1 本期必须做（MVP 必做）
- ReleaseWindow：CRUD + 状态流转 + 列表/详情
- Project / Repository / SubProject：基础管理
- BranchRule：可配置（至少支持模板 + 正则校验）
- VersionPolicy：可配置（SemVer 为主，支持 bump：major/minor/patch）
- VersionUpdater：Maven 版本更新（pom.xml），Gradle 先做基础路径
- UI：菜单结构、核心页面可用

### 4.2 本期不做（明确排除）
- 真正的 Git 操作（创建分支/合并/打 tag）——先留接口，不做落地
- 权限体系（RBAC）——先做简单本地账户/无鉴权模式
- 通知（飞书/钉钉/邮件）——后续 Roadmap
- CI/CD 深度集成（Jenkins/GitHub Actions）——后续 Roadmap

---

## 5. 总体架构

### 5.1 技术栈
- 后端：Java 21 + Spring Boot 3.2.x
- 架构：DDD + 模块化单体（Modular Monolith）
- 持久化：Spring Data JPA（MVP），后续可替换为 MyBatis/JOOQ
- 数据库：PostgreSQL（建议）/ MySQL（可选）
- 前端：Vue 3 + Vite + Element Plus
- 构建：Maven（Flatten Plugin）

### 5.2 后端模块建议（按 DDD 分层 + 边界）
- `releasehub-api`：controller + request/response DTO
- `releasehub-app`：application service（用例编排、事务边界）
- `releasehub-domain`：聚合根、实体、值对象、领域服务、策略接口
- `releasehub-infra`：JPA 实现、外部系统适配（Git/CI/通知未来扩展点）
- `releasehub-bootstrap`：启动模块（Spring Boot）

### 5.3 前端模块建议
- `src/views`：按业务域划分（release-window / project / rules / settings）
- `src/components`：可复用组件（RuleEditor、DiffViewer、StatusTag 等）
- `src/api`：后端 API 封装

---

## 6. 核心领域模型（MVP 版本）

### 6.1 聚合与实体

#### ReleaseWindow（聚合根）
- id
- name
- startAt / endAt
- targetVersion（值对象）
- status（Draft/Planned/Ready/Executing/Released/Cancelled）
- scope：关联 ReleaseWindowItem（仓库/子项目维度）
- audit：createdBy/createdAt/updatedAt

ReleaseWindowItem（实体）
- repositoryId
- subProjectId（可空）
- plannedBranch（可由 BranchRule 推导）
- plannedVersion（可由 VersionPolicy 推导）
- executionResult（lastRunId / status / message）

#### Project（聚合根）
- id, name, description
- repositories（RepositoryRef 列表）

#### Repository（聚合根）
- id
- name
- gitUrl
- defaultBranch
- buildTool（MAVEN/GRADLE）

#### SubProject（实体/聚合，视复杂度）
- repositoryId
- path
- artifactId / moduleName

#### BranchRule（聚合根）
- id
- name
- template（例如 `release/{version}`）
- regex（校验用）
- appliesTo（项目/仓库范围）

#### VersionPolicy（聚合根）
- id
- name
- scheme（SEMVER/DATE/自定义）
- bumpRule（major/minor/patch/rc）

### 6.2 状态流转（ReleaseWindow）
- Draft → Planned → Ready → Executing → Released
- Draft/Planned/Ready → Cancelled
- Executing → Planned（允许回滚到规划态，需审计）

守卫条件示例：
- Planned → Ready：必须绑定至少一个 Repository
- Ready → Executing：必须完成规则校验（branch/version 可推导且合法）

---

## 7. API 设计（草案）

### 7.1 Release Window
- `POST /api/release-windows`
- `GET /api/release-windows?from=&to=&projectId=&status=`
- `GET /api/release-windows/{id}`
- `PUT /api/release-windows/{id}`
- `POST /api/release-windows/{id}/transition`（status 迁移）
- `POST /api/release-windows/{id}/items`（绑定仓库/子项目）
- `POST /api/release-windows/{id}/validate`（校验 branch/version）
- `POST /api/release-windows/{id}/execute/version-update`（执行版本更新，返回 runId）
- `GET /api/runs/{runId}`（查看执行结果与 diff）

### 7.2 Project/Repository/SubProject
- `POST /api/projects` / `GET /api/projects` / `GET /api/projects/{id}`
- `POST /api/repositories` / `GET /api/repositories`
- `POST /api/repositories/{id}/scan-subprojects`（可选：后续通过 git clone/本地路径扫描，MVP 可手填）

### 7.3 Rules
- `POST /api/branch-rules` / `GET /api/branch-rules`
- `POST /api/version-policies` / `GET /api/version-policies`

---

## 8. 前端菜单结构（MVP）
1) **发布窗口**
- 发布窗口总览（列表/日历）
- 发布窗口详情（状态、范围、目标版本、执行记录）

2) **项目管理**
- 项目列表/详情
- 仓库管理
- 子项目管理（可选：与仓库详情合并）

3) **规则中心**
- 分支命名规则
- 版本策略

4) **执行记录**
- 版本更新运行记录（run 列表 + diff 查看）

5) **系统设置**
- 环境配置（Maven/Gradle 更新器参数）
- 未来：鉴权、通知、CI 适配

---

## 9. 版本号管理实现策略（关键设计）

### 9.1 Maven（优先落地）
- 目标：更新 `pom.xml` 的 `<version>`（项目版本）以及可选的子模块版本一致性。
- 约束：Flatten Plugin 会生成扁平 POM（用于发布），但**源 pom.xml 仍需保持真实版本**。
- 实现建议：
  - 解析 XML（DOM/SAX/JSoup 均可，建议用稳妥的 XML 解析库）
  - 只更新顶层 `<project><version>`，并支持：
    - 多模块：父 POM 统一版本 → 子模块继承版本（不写 version）
    - 如果子模块有显式 version，则提供策略：保持/统一/报错
  - 输出 diff（更新前后内容片段）

### 9.2 Gradle（MVP 简化）
- 首选：更新 `gradle.properties` 的 `version=`
- 备选：若写在 `build.gradle`，MVP 先提示“手动处理/不支持”，后续再做 AST 级改写。

---

## 10. 里程碑规划（按 1–2 小时/天）

> 核心原则：先把“可用闭环”跑起来，再加深自动化与体验。你现在最缺的不是设计，而是可运行的纵向切片。

### Milestone 0：工程骨架与质量门禁（1–2 天）
- Spring Boot 3.2 + Java 21 基础骨架
- DDD 分层模块与依赖约束
- 基础工程约定：Checkstyle/Spotless（可选）、测试框架、统一异常
- 简单健康检查与 OpenAPI（springdoc）

### Milestone 1：发布窗口闭环（5–7 天）
- ReleaseWindow 聚合 + JPA 持久化
- CRUD + 状态流转
- 前端：发布窗口列表/详情

### Milestone 2：项目/仓库/规则中心（5–7 天）
- Project/Repository 基础管理
- BranchRule/VersionPolicy 配置管理
- ReleaseWindow 绑定仓库范围（items）

### Milestone 3：版本更新器（7–10 天）
- Maven 版本更新器（pom.xml）落地 + diff 结果
- 执行记录（Run）存储与展示
- Gradle properties 版支持

### Milestone 4：体验与可扩展性（持续迭代）
- 校验器增强（规则预览、冲突提示）
- 模板化策略工厂
- 预留 Git/CI/通知适配接口

---

## 11. Day-by-Day 执行清单（建议版）

> 以“每天 1–2 小时”为硬约束，避免陷入过度设计。每一天必须产出可提交的增量。

### Day 1：骨架与模块
- 创建多模块 Maven 工程（bootstrap/app/domain/infra/api）
- Spring Boot 启动、H2 或本地 Postgres 连接
- 统一异常与返回体

### Day 2：ReleaseWindow 领域模型 + JPA
- 聚合根、状态枚举、实体映射
- Repository（JPA）与基本测试

### Day 3：ReleaseWindow API（CRUD）
- Controller + DTO + MapStruct
- 分页列表、详情

### Day 4：状态流转用例
- transition 接口 + 守卫条件
- 审计字段

### Day 5：前端框架 + 发布窗口页面
- Vue3+Vite+ElementPlus 初始化
- 菜单 + 列表 + 详情页骨架

### Day 6：Project/Repository 基础管理
- 后端：实体 + CRUD
- 前端：列表/编辑

### Day 7：规则中心（BranchRule/VersionPolicy）
- 后端：配置实体 + CRUD
- 前端：Rule 表单（模板 + regex）

### Day 8：ReleaseWindow 绑定仓库范围
- items 设计 + API
- 详情页可绑定/移除

### Day 9：校验接口 validate
- 根据 BranchRule/VersionPolicy 推导 plannedBranch/Version
- 返回校验结果（OK/ERROR + message）

### Day 10：Maven VersionUpdater（核心）
- pom.xml 更新实现（只改版本）
- 产出 diff（文本前后对比）

### Day 11：执行记录 Run
- Run 表 + 存储执行结果
- UI：查看 run 列表 + 详情（diff）

### Day 12：Gradle properties 支持
- gradle.properties 的 version 改写
- 不支持场景给出明确提示

> Day 13+：体验优化、规则预览、子项目支持、冲突检测。

---

## 12. 风险清单与对策

1) **版本改写的工程差异巨大**（多模块、父子版本、properties 管理）
- 对策：MVP 只支持“父 POM 版本”改写；复杂场景以策略/报错提示处理。

2) **过早做 Git/CI 集成导致复杂度爆炸**
- 对策：第一期只做“版本文件改写 + diff + run”，Git 操作只留接口。

3) **规则中心泛化过度**
- 对策：模板 + 正则足够；策略工厂先做接口，具体实现后续渐进。

4) **前端耗时失控**
- 对策：Element Plus 组件化，页面优先“能用”；美观最后做。

---

## 13. 质量门禁（建议）
- 单元测试：领域服务、版本更新器必须覆盖
- 集成测试：ReleaseWindow 状态流转 + validate
- 数据库迁移：Flyway/Liquibase（MVP 可延后，但建议尽早接入）
- API 文档：OpenAPI 自动生成

---

## 14. Roadmap（未来能力）
- GitOps：自动建分支、打 tag、生成 changelog
- CI/CD 集成：Jenkins/GHA 触发、发布结果回写
- 通知：飞书/钉钉/邮件
- 权限：RBAC + 审计
- SaaS：多租户、配额、计费
- IDE 插件：从 IDE 读取当前版本、快速创建发布窗口

---

## 15. 你接下来最该做的三件事（不讨好版）
1) **别再“想清楚再开始”**：你已经想得够多了，缺的是第一条可运行的纵向切片。
2) **MVP 只盯一个闭环**：ReleaseWindow（CRUD+状态）→ 绑定仓库 → validate → Maven 版本更新 → run 结果。
3) **把“复杂情况”当成二期**：多模块细节、Git/CI、通知、权限，全部后移。

---

# 附录 A：建议的仓库目录结构（示例）
```
releasehub/
  releasehub-bootstrap/
  releasehub-api/
  releasehub-app/
  releasehub-domain/
  releasehub-infra/
  releasehub-web/        # Vue3
  docs/
```

# 附录 B：ReleaseWindow 状态枚举建议
- DRAFT
- PLANNED
- READY
- EXECUTING
- RELEASED
- CANCELLED

