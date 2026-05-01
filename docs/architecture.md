# ReleaseHub 架构文档

## 1. 项目概述

ReleaseHub 是一个**发布编排与自动化平台**，帮助团队管理多仓库、多迭代的发布流程。核心能力：

- **发布窗口管理**：创建、发布、冻结、关闭发布窗口
- **迭代与仓库关联**：将多个迭代及其关联的代码仓库挂载到发布窗口
- **GitFlow 分支自动化**：自动创建 release 分支、合并 feature 分支、打标签、触发 CI
- **版本策略管理**：统一的版本号推导、校验、批量更新（Maven/Gradle）
- **冲突预检**：版本冲突、分支冲突、合并冲突、跨仓库版本一致性检测
- **编排执行**：将发布流程分解为有序任务并执行

## 2. 系统架构

项目采用**六边形架构（Ports & Adapters）**，分为 6 个 Maven 模块：

```
releasehub-bootstrap       ← Spring Boot 入口 + 配置 + 集成测试
    ↓
releasehub-interfaces      ← REST Controllers + DTOs + 全局异常处理
    ↓
releasehub-application     ← 应用服务 + 用例编排 + 端口接口（Port）
    ↓
releasehub-domain          ← 纯领域模型（无框架依赖）
    ↓
releasehub-common          ← 共享工具（异常、分页、响应包装）

releasehub-infrastructure  ← 适配器实现（JPA、GitLab/GitHub、Security）
    ↓ (实现 application 层的 Port)
releasehub-application
```

### 依赖规则

| 模块 | 可依赖 |
|------|--------|
| bootstrap | interfaces, infrastructure |
| interfaces | application, common, domain |
| infrastructure | application, domain, common |
| application | domain, common |
| domain | common |
| common | （无内部依赖） |

> 规则由 Maven Enforcer Plugin 和 ArchUnit 测试强制执行。

## 3. 技术栈

| 组件 | 版本/选型 |
|------|-----------|
| JDK | 21 |
| 框架 | Spring Boot 3.4.1 |
| 构建 | Maven 3.9+ |
| 数据库 | PostgreSQL（生产）/ H2（测试） |
| 迁移 | Flyway 10.20（生产）/ JPA DDL（本地开发） |
| ORM | Spring Data JPA |
| 安全 | Spring Security + JWT (jjwt 0.11.5) |
| API 文档 | SpringDoc OpenAPI 2.6 |
| 测试 | JUnit 5, Mockito, AssertJ, Testcontainers, ArchUnit |
| 简化代码 | Lombok 1.18 |

## 4. 领域模型

系统包含 11 个限界上下文：

```
ReleaseWindow ──1:N──> WindowIteration ──N:1──> Iteration
     │                                                │
     │                                                │ N:M
     │                                                │
     └──> Run ──1:N──> RunItem                        Repo
              │                                       │
              └──1:N──> RunTask ──> (8 executors)     │
                                                      │
Group ──1:N──> Repo <── N:M ── Iteration             │
                                                      │
VersionPolicy ──> VersionDeriver/Extractor            │
                                                      │
BranchRule ──> 分支命名校验                             │
                                                      │
ConflictReport ──> ConflictItem (4种检测)             │
                                                      │
User ──> JWT Authentication                           │
```

### 核心实体

| 实体 | 说明 |
|------|------|
| **ReleaseWindow** | 发布窗口，状态：DRAFT → PUBLISHED → FROZEN → CLOSED |
| **Iteration** | 迭代/版本，关联多个 CodeRepository |
| **CodeRepository** | 代码仓库，支持 GitLab / GitHub / MOCK 三种 Provider |
| **WindowIteration** | 窗口与迭代的关联，记录 release 分支和合并信息 |
| **Run / RunTask** | 编排执行记录，Run 包含多个 RunTask |
| **RunItem / RunStep** | 新的编排模型（逐步迁移中） |
| **VersionPolicy** | 版本策略，定义版本号推导规则 |
| **ConflictReport** | 冲突检测报告，包含多个 ConflictItem |
| **BranchRule** | 分支命名规则 |
| **Group** | 仓库分组 |
| **User** | 用户，通过 JWT 认证 |

## 5. 核心业务流程

### 5.1 发布窗口生命周期

```
创建 (DRAFT) → 发布 (PUBLISHED) → 冻结 (FROZEN) → 关闭 (CLOSED)
                  ↑                    |
                  └── 解冻 ────────────┘
```

- **DRAFT**：可配置迭代、仓库、版本策略
- **PUBLISHED**：对外可见，可执行操作
- **FROZEN**：冻结不可修改，保护已发布的配置
- **CLOSED**：关闭窗口，触发收尾编排（归档分支、关闭迭代）

### 5.2 迭代挂载流程

```
1. 挂载迭代到发布窗口 (attach)
2. 为每个仓库创建 release/{windowKey} 分支 (从 defaultBranch)
3. 将 feature/{iterationKey} 分支合并到 release/{windowKey}
4. 记录 releaseBranch / branchCreated / lastMergeAt
```

### 5.3 发布编排任务

发布流水线生成 6 类任务（ReleaseRunService.generateReleaseTasks）：

| 顺序 | 任务类型 | 执行器 |
|------|----------|--------|
| 1 | CLOSE_ITERATION | CloseIterationExecutor |
| 2 | ARCHIVE_FEATURE_BRANCH | ArchiveFeatureBranchExecutor |
| 3 | UPDATE_POM_VERSION | UpdatePomVersionExecutor (占位) |
| 4 | MERGE_RELEASE_TO_MASTER | MergeReleaseToMasterExecutor |
| 5 | CREATE_TAG | CreateTagExecutor |
| 6 | TRIGGER_CI_BUILD | TriggerCiBuildExecutor |

### 5.4 冲突检测体系

在发布执行前自动检测 4 类冲突：

| 类型 | 检测内容 |
|------|----------|
| 版本冲突 | 系统记录版本 vs 仓库分支实际版本是否一致 |
| 分支冲突 | feature/release 分支是否已存在、分支命名是否合规 |
| 合并冲突 | feature→release 和 release→master 的 MR/PR 是否可合并 |
| 跨仓库冲突 | 同一迭代内不同仓库的目标版本是否一致 |

## 6. Git Provider 多适配器架构

```
GitBranchPort (接口)
    ├── GitLabGitBranchAdapter    (GitLab API)
    ├── GitHubGitBranchAdapter    (GitHub API)
    └── MockGitBranchAdapter      (内存 Mock，测试用)

GitBranchAdapterFactory
    └── GitBranchAdapterFactoryImpl  (根据 GitProvider 枚举路由)
```

所有分支操作（createBranch, mergeBranch, deleteBranch, archiveBranch, createTag, triggerPipeline, getBranchStatus, checkMergeability）统一通过 `GitBranchAdapterFactory.getAdapter(provider)` 获取适配器执行。

## 7. API 概览

基础路径：`/api/v1`

### 认证
- `POST /auth/login` — 登录获取 JWT Token
- `GET /auth/me` — 获取当前用户信息

### 发布窗口
- `POST /release-windows` — 创建
- `GET /release-windows/{id}` — 详情
- `GET /release-windows` / `GET /release-windows/paged` — 列表
- `POST /release-windows/{id}/publish` — 发布
- `POST /release-windows/{id}/freeze` / `unfreeze` — 冻结/解冻
- `POST /release-windows/{id}/close` — 关闭（触发收尾编排）
- `GET /release-windows/{id}/branch-status` — 分支状态

### 迭代管理
- `POST /release-windows/{id}/attach` / `detach` — 挂载/卸载迭代
- `GET /release-windows/{id}/iterations` — 窗口迭代列表
- `POST /release-windows/{id}/iterations/{key}/create-release-branch` — 创建 release 分支
- `POST /release-windows/{id}/iterations/{key}/merge-feature` — 合并 feature 分支

### 版本管理
- `POST /release-windows/{id}/execute/version-update` — 单仓库版本更新
- `POST /release-windows/{id}/execute/batch-version-update` — 批量版本更新
- `POST /release-windows/{id}/validate` — 版本校验
- `GET /version-policies` — 版本策略列表

### 代码仓库 & 其他
- `GET/POST/PUT/DELETE /repositories` — 仓库 CRUD
- `GET /groups` / `GET /iterations` — 分组/迭代查询
- `GET /dashboard` — 仪表盘统计
- `GET /settings` — 系统设置
- `POST /release-windows/{id}/orchestrate` — 执行编排
- `POST /release-windows/{id}/conflicts` — 冲突检测

## 8. 配置环境

| Profile | 数据库 | JPA DDL | Flyway | 用途 |
|---------|--------|---------|--------|------|
| local | PostgreSQL | update | disabled | 本地开发 |
| test | H2 | none | enabled | 集成测试 |
| unitTest | H2 | none | enabled | 单元测试 |
| e2e | PostgreSQL (Testcontainers) | validate | enabled | E2E 测试 |
| prd | PostgreSQL | validate | enabled | 生产环境 |
