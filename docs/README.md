# ReleaseHub

**多仓库发布编排与自动化平台** — 统一管理多个 Git 仓库的发布窗口、版本策略和分支规则。

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-green.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5+-blue.svg)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9+-blue.svg)](https://www.typescriptlang.org/)

## 项目目标

解决多仓库场景下的发布协调问题：
- **认知成本**：跨仓库的版本、分支、窗口信息碎片化 → 统一发布窗口规划
- **协调成本**：发布窗口冲突、跨仓库版本不一致 → 迭代与仓库关联管理
- **执行成本**：手动版本号更新易出错、难规模化 → 自动化版本管理（Maven/Gradle）

## 快速开始

```bash
# 启动 PostgreSQL（Docker）
cd docs && docker compose up -d

# 启动后端
cd backend && ./scripts/run.sh

# 启动前端
cd frontend && pnpm dev
```

详细指南请参阅 [快速启动文档](context/tech/README_START.md)

## 项目结构

```
release-hub/
├── backend/                          # ☕ 后端 (Java 21 + Spring Boot 3.4)
│   ├── releasehub-domain/            # 领域层（纯业务逻辑，无框架依赖）
│   ├── releasehub-application/       # 应用层（用例编排 + Port 接口）
│   ├── releasehub-infrastructure/    # 基础设施层（JPA、GitLab/GitHub 适配器）
│   ├── releasehub-interfaces/        # 接口层（REST API + DTO）
│   ├── releasehub-common/            # 公共模块（异常、分页、响应封装）
│   ├── releasehub-bootstrap/         # 启动模块（Spring Boot 入口 + 配置）
│   ├── scripts/                      # 后端脚本
│   └── tests/                        # Shell 测试脚本
│
├── frontend/                         # 🌐 前端 (Vue 3 + TypeScript + Vite)
│   ├── src/
│   │   ├── views/                    # 页面视图（15 个模块）
│   │   ├── components/               # 可复用组件
│   │   ├── composables/              # Vue 组合式函数
│   │   ├── api/                      # API 客户端
│   │   ├── stores/                   # Pinia 状态管理
│   │   ├── router/                   # 路由配置
│   │   └── i18n/                     # 国际化（zh-CN / en-US）
│   ├── e2e/                          # E2E 测试 (Puppeteer)
│   ├── scripts/                      # 前端脚本
│   └── plop/                         # 代码生成器模板
│
├── docs/                             # 📚 项目文档
│   ├── context/                      #   知识库
│   │   ├── business/                 #     业务上下文（领域模型、用户故事、项目规划）
│   │   ├── tech/                     #     技术上下文（架构、API、规范、仓库快照）
│   │   └── experience/              #     经验沉淀（经验教训 + 审计报告）
│   ├── openspec/                     #   规格驱动开发
│   │   ├── specs/                    #     功能规格（9 个模块）
│   │   └── changes/archive/          #     已归档变更提案（11 个）
│   ├── workflow/                     #   工作流定义（5 阶段）
│   ├── requirements/                 #   需求文档（7 个已完成）
│   ├── deployment.md                 #   部署指南
│   └── docker-compose.yml            #   PostgreSQL 容器配置
│
├── tasks/                            # 📋 任务追踪（蓝图/切片体系）
│   ├── plans/                        #   阶段规划
│   ├── records/                      #   执行日志
│   └── templates/                    #   蓝图 + 切片日志模板
│
└── scripts/                          # 🔧 项目级脚本
```

## 技术架构

### 后端
- **框架**: Spring Boot 3.4.x + Spring Framework 6.2.x
- **架构**: DDD 六边形架构（6 个 Maven 模块，ArchUnit + Maven Enforcer 强制执行分层依赖）
- **数据库**: PostgreSQL + Spring Data JPA + Flyway
- **认证**: Spring Security + JWT (jjwt 0.11.5)
- **API 文档**: SpringDoc OpenAPI 2.6

### 前端
- **框架**: Vue 3.5+ (Composition API)
- **语言**: TypeScript 5.9+
- **构建**: Vite (rolldown-vite)
- **UI**: Element Plus 2.12
- **状态**: Pinia 3.0
- **路由**: Vue Router 4.6
- **国际化**: vue-i18n 9

### 测试
- **后端**: JUnit 5 + Testcontainers + ArchUnit（134 测试全通过）
- **前端**: Vitest + Puppeteer（62 E2E 全通过）
- **静态分析**: SpotBugs (0 bugs)

## 核心概念

| 概念 | 描述 |
|------|------|
| **ReleaseWindow** | 发布窗口，有时间限制的发布计划，状态机驱动 |
| **Iteration** | 迭代/冲刺，聚合关联仓库，桥接窗口与仓库 |
| **CodeRepository** | 代码仓库，集成 GitLab/GitHub，支持分支/MR 统计 |
| **VersionPolicy** | 版本策略（MAJOR/MINOR/PATCH/DATE）+ 版本推导 |
| **BranchRule** | 分支规则（TEMPLATE/REGEX 双模式）+ 分支命名校验 |
| **Run / RunTask** | 执行记录，10 种任务执行器，支持重试与导出 |
| **Group** | 层级分组树，code 自动生成，叶子节点约束 |
| **Conflict Detection** | 7 种冲突类型预检（MISMATCH / REPO_AHEAD / SYSTEM_AHEAD / BRANCH_EXISTS / BRANCH_NONCOMPLIANT / CROSS_REPO_VERSION_MISMATCH / MERGE_CONFLICT） |

### 发布窗口生命周期

```
DRAFT → PUBLISHED → CLOSED
       ↕ (frozen/unfreeze)
```

## 开发指南

### 后端开发

```bash
cd backend

# 构建
mvn clean install -DskipTests

# 运行
./scripts/run.sh

# 运行测试
mvn test

# API 文档
open http://localhost:8080/swagger-ui.html
```

### 前端开发

```bash
cd frontend

# 安装依赖
pnpm install

# 启动开发服务器
pnpm dev

# API 类型生成（后端 API 变更后）
pnpm gen:api

# 类型检查 + 代码检查
pnpm typecheck && pnpm lint
```

### 测试

```bash
# 后端单测（surefire，< 30s）
cd backend && mvn test

# 后端集成+E2E（failsafe）
cd backend && mvn verify

# 后端覆盖率
cd backend && mvn verify -Pcoverage

# 前端单测
cd frontend && pnpm test

# 前端 E2E（Playwright）
cd frontend && pnpm test:e2e

# 前端 E2E UI 模式
cd frontend && pnpm test:e2e:ui
```

## 规格驱动开发

本项目使用 OpenSpec 进行规格驱动开发：

```bash
# 查看进行中的变更
openspec list

# 验证提案
openspec validate --strict
```

详情参阅 [OpenSpec 指南](openspec/AGENTS.md)

## 相关链接

| 资源 | 链接 |
|------|------|
| 项目规划 | [context/business/project-plan.md](context/business/project-plan.md) |
| 领域模型 | [context/business/domain-model.md](context/business/domain-model.md) |
| 发布窗口 API | [context/tech/api/release-window.md](context/tech/api/release-window.md) |
| 用户故事 | [context/business/user-stories-v1.3.md](context/business/user-stories-v1.3.md) |
| 部署指南 | [deployment.md](deployment.md) |
| 开发规范 | [context/tech/conventions/](context/tech/conventions/) |
| 任务追踪 | [../tasks/README.md](../tasks/README.md) |

## License

Private - All Rights Reserved
