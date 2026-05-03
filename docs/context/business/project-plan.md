# ReleaseHub 项目总体规划书

> 版本：v3.1
> 最后更新：2026-05-02（Phase 1-7 + P0 治理收尾完成）
> 状态：MVP 核心功能已完成，v0.1.9 质量基线清零

---

## 1. 背景与问题定义

ReleaseHub 面向「多项目、多仓库」场景，统一管理发布窗口、版本号演进、分支策略与迭代管理，解决团队在多仓协作中常见的三类成本：

| 成本类型 | 问题描述 | 解决方案 |
|---------|---------|---------|
| **认知成本** | 版本、分支、窗口分散在各仓库/文档中 | 发布窗口统一规划与展示 |
| **协调成本** | 发布窗口冲突、跨仓版本不一致 | 迭代与仓库关联管理 |
| **执行成本** | 版本号更新依赖人工，易错且难规模化 | 版本号自动管理（Maven/Gradle） |

**项目愿景**：成为「发布节奏的单一事实源（Single Source of Truth）」。

---

## 2. 项目目标与完成状态

### 2.1 核心目标

| 目标 | 状态 |
|------|------|
| 发布窗口统一规划与展示（列表/详情/状态流转） | ✅ 已完成 |
| 版本号自动管理（Maven pom.xml / Gradle gradle.properties） | ✅ 已完成 |
| 分支规则配置（BranchRule） | ✅ 已完成 |
| 版本校验与推导（VersionPolicy） | ✅ 已完成 |

### 2.2 交付标准

| 标准 | 状态 |
|------|------|
| 从 UI 创建窗口 → 绑定迭代 → 提测合并 → 收尾编排 → 查看运行记录 | ✅ 已完成 |
| 核心策略（版本策略/更新器）可插拔 | ✅ 已完成 |
| 审计记录（createdAt/updatedAt） | ✅ 已完成 |

---

## 3. 术语表

| 术语 | 描述 |
|------|------|
| **ReleaseWindow** | 发布窗口，具有起止时间、状态、影响范围 |
| **Iteration** | 迭代，聚合一组相关的代码仓库 |
| **WindowIteration** | 窗口与迭代的 N:N 关联 |
| **CodeRepository** | Git 仓库实体（集成 GitLab） |
| **VersionPolicy** | 版本递增策略（MAJOR/MINOR/PATCH/DATE） |
| **VersionUpdater** | 版本更新执行器（Maven/Gradle） |
| **Run** | 版本更新的执行记录 |
| **BranchRule** | 分支规则与合规校验 |
| **RunTask** | 运行任务与执行步骤 |

> 详细领域模型参见 `docs/context/business/domain-model.md`

---

## 4. 范围边界

### ✅ MVP 已完成
- ReleaseWindow：CRUD + 状态流转 + 冻结机制
- Iteration：迭代管理 + 仓库关联 + 窗口关联
- CodeRepository：仓库 CRUD + GitLab 集成
- BranchRule：规则 CRUD + 合规校验
- VersionUpdater：Maven/Gradle 版本更新 + Diff 生成
- Run：执行记录 + 导出/重试
- RunTask：10 种任务执行器
- 发布准备/收尾：合并与编排 API + Publish 自动编排（事件驱动）
- 前端 UI：核心页面全部可用

### ⚠️ 待完善
- Attach 分支操作集成 Run 追踪（技术债务，较大架构调整，择期处理）
- 预存 SpotBugs EI_EXPOSE_REP x5（`releasehub-common` 基础类，全局影响）

### ❌ 明确排除（当前版本不实现）
- 项目管理（Project）
- 权限体系（RBAC）
- 通知（飞书/钉钉/邮件）
- CI/CD 深度集成

> 注：通知和 RBAC 在早期 Roadmap 中列为 Phase 3/4，但经评估不属于 MVP 范围。当前 Roadmap 中 Phase 6 标记为"待规划"，上述能力将根据用户反馈决定优先级。

---

## 5. 技术架构

### 5.1 技术栈

| 层级 | 技术选型 |
|------|---------|
| 后端 | Java 21 + Spring Boot 3.4.1 + Spring Data JPA |
| 数据库 | PostgreSQL + Flyway |
| 安全 | Spring Security + JWT |
| 前端 | Vue 3.5 + TypeScript + Vite + Element Plus |

### 5.2 模块结构

```
backend/                          # 后端（DDD 分层）
├── releasehub-domain/            # 聚合根、实体（无框架依赖）
├── releasehub-application/       # 应用服务、Port 接口
├── releasehub-infrastructure/    # JPA 实现、VersionUpdater
├── releasehub-interfaces/        # REST 控制器
└── releasehub-bootstrap/         # Spring Boot 启动

frontend/                         # 前端（Vue 3）
├── src/views/                    # 按业务域划分页面
├── src/api/                      # API 封装
└── src/components/               # 可复用组件
```

**架构约束**（ArchUnit 强制）：`domain` 禁止依赖 Spring/JPA

---

## 6. 核心领域模型

### 实体关系

```
┌─────────────────┐        N:N           ┌─────────────────┐
│  ReleaseWindow  │◄────────────────────►│    Iteration    │
│    (发布窗口)    │   WindowIteration    │     (迭代)       │
└────────┬────────┘                      └────────┬────────┘
         │ 1:N                                    │ N:N
         ▼                                        ▼
┌─────────────────┐                      ┌─────────────────┐
│      Run        │                      │  CodeRepository │
└─────────────────┘                      └─────────────────┘
```

### 状态流转

```
ReleaseWindow: DRAFT → PUBLISHED → CLOSED
```

> 文案映射：待发布（DRAFT）→ 已发布（PUBLISHED）→ 已关闭（CLOSED）

---

## 7. API 概览

### 核心 API（已实现）

| 模块 | 路径前缀 | 关键操作 |
|------|---------|---------|
| ReleaseWindow | `/api/v1/release-windows` | CRUD, publish, freeze, attach/detach, plan, orchestrate, merge, validate |
| Iteration | `/api/v1/iterations` | CRUD, repos add/remove |
| Repository | `/api/v1/repositories` | CRUD, sync, branch summary |
| BranchRule | `/api/v1/branch-rules` | CRUD, enable/disable, test, 合规检查 |
| Run | `/api/v1/runs` | 查询, 导出, 重试 |
| Group | `/api/v1/groups` | tree, CRUD |
| Settings | `/api/v1/settings/gitlab` | get/save/test |

### 待实现 API

| 模块 | 路径前缀 |
|------|---------|
| 暂无 | - |

> 完整 API 文档：`http://localhost:8080/swagger-ui.html`

---

## 8. 版本更新器

### Maven ✅
- 解析 pom.xml（DOM）
- 更新 `<project><version>`
- 生成 Diff

### Gradle ✅
- 更新 `gradle.properties` 中的 `version=`
- build.gradle 内嵌版本暂不支持

---

## 9. 里程碑与质量

### 完成状态

| 里程碑 | 状态 |
|--------|------|
| M0 工程骨架 | ✅ |
| M1 发布窗口闭环 | ✅ |
| M2 迭代/仓库/设置 | ✅ |
| M3 版本更新器 | ✅ |
| M4 体验增强 | ✅ 已完成 |
| M5 前端对齐 | ✅ 已完成 |

### 测试覆盖

- ✅ 后端测试：134/134 通过（52 单元/集成 + 82 E2E TestContainers）
- ✅ 前端 E2E：62/62 通过（Playwright, 6 套件）
- ✅ 架构测试：ArchUnit（11 个测试通过）
- ✅ 前端 typecheck / lint 通过

### 待完成任务

> 2026-05-02 更新：Phase 1-7 已全部完成（v0.1.8）。P0 治理收尾已完成（v0.1.9）。当前无阻塞性待办项。

- Attach 分支操作集成 Run 追踪（技术债务，较大架构调整，择期处理）
- 预存 SpotBugs EI_EXPOSE_REP 修复（`releasehub-common` 基础类，全局影响）

---

## 10. Roadmap

| 阶段 | 内容 | 状态 |
|------|------|------|
| **Phase 1** | 前端对齐：BranchRule 新 API 对接、Version Ops Dashboard | ✅ 已完成 |
| **Phase 2** | 体验增强：日历视图（月/周 + 冲突可视化） | ✅ 已完成 |
| **Phase 3** | 部署 & 发布自动化：部署文档、Attach 错误可见性、Publish 自动编排 | ✅ 已完成 |
| **Phase 4** | E2E & 质量：TestContainers E2E 补齐、静态扫描脚本、文档一致性 | ✅ 已完成 |
| **Phase 5** | 技术债务清理：预存 SpotBugs 修复、Attach Run 追踪集成 | 📋 择期 |
| **Phase 6** | 待规划：通知/RBAC/性能优化/变更日志/异步执行 — 根据用户反馈决定优先级 | 📋 待规划 |

---

## 11. 快速开始

```bash
# PostgreSQL
cd docs && docker compose up -d

# 后端
cd backend && mvn -q spring-boot:run -pl releasehub-bootstrap -Dspring-boot.run.profiles=local

# 前端
cd frontend && pnpm dev

# 访问
# 前端: http://localhost:5173 (admin/admin)
# API 文档: http://localhost:8080/swagger-ui.html
```

---

## 附录：快速参考

### 状态枚举

| 实体 | 状态值 |
|------|--------|
| ReleaseWindow | DRAFT → PUBLISHED → CLOSED |
| Run | RUNNING / SUCCESS / FAILED / PARTIAL |

### 相关文档

| 文档 | 描述 |
|------|------|
| `FUNCTION_DEVELOPMENT_PLAN.md` | 功能开发规划 |
| `NEXT_STEPS_TASKS.md` | 待办任务清单 |
| `docs/context/business/domain-model.md` | 详细领域模型 |
