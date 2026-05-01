# ReleaseHub

**多仓库发布协调平台** - 统一管理多个 Git 仓库的发布窗口、版本策略和分支规则。

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-green.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5+-blue.svg)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9+-blue.svg)](https://www.typescriptlang.org/)

## 🎯 项目目标

解决多仓库场景下的发布协调问题：
- **统一发布节奏**：跨仓库的版本发布窗口管理
- **降低协调成本**：自动化版本更新和分支管理
- **减少执行错误**：基于策略的版本校验

## 🚀 快速开始

```bash
# 启动后端（含数据库）
./scripts/dev/start-backend.sh

# 启动前端
cd release-hub-web && pnpm dev
```

详细指南请参阅 [快速启动文档](docs/getting-started/README_START.md)

## 📁 项目结构

```
releasehub/
├── docs/                       # 📚 项目文档
│   ├── getting-started/        # 入门指南
│   ├── architecture/           # 架构设计
│   ├── api/                    # API 文档
│   ├── planning/               # 项目规划
│   └── reports/                # 项目报告
│
├── scripts/                    # 🔧 项目级脚本
│   ├── dev/                    # 开发脚本
│   └── tools/                  # 通用工具
│
├── openspec/                   # 📋 规格驱动开发
│   ├── changes/                # 变更提案
│   └── specs/                  # 功能规格
│
├── release-hub/                # ☕ 后端 (Java 21 + Spring Boot)
│   ├── releasehub-domain/      # 领域层（纯业务逻辑）
│   ├── releasehub-application/ # 应用层（用例编排）
│   ├── releasehub-infrastructure/ # 基础设施层
│   ├── releasehub-interfaces/  # 接口层（REST API）
│   ├── releasehub-common/      # 公共模块
│   ├── releasehub-bootstrap/   # 启动模块
│   ├── scripts/                # 后端脚本
│   └── tests/                  # Shell 测试脚本
│
├── release-hub-web/            # 🌐 前端 (Vue 3 + TypeScript)
│   ├── src/                    # 源代码
│   └── e2e/                    # E2E 测试 (Puppeteer)
│
└── docker-compose.yml          # Docker 配置
```

## 🏗️ 技术架构

### 后端
- **框架**: Spring Boot 3.4.x + Spring Framework 6.2.x
- **架构**: DDD (Domain-Driven Design) 分层架构
- **数据库**: PostgreSQL + Spring Data JPA
- **迁移**: Flyway
- **认证**: Spring Security + JWT

### 前端
- **框架**: Vue 3.5+ (Composition API)
- **语言**: TypeScript 5.9+
- **构建**: Vite (rolldown-vite)
- **UI**: Element Plus
- **状态**: Pinia

## 📖 核心概念

| 概念 | 描述 |
|------|------|
| **ReleaseWindow** | 发布窗口，有时间限制的发布计划 |
| **VersionPolicy** | 版本策略，定义版本号递增规则 |
| **BranchRule** | 分支规则，用于分支命名验证 |
| **CodeRepository** | 代码仓库，集成 GitLab |
| **Run** | 运行记录，版本更新的执行历史 |

### 发布窗口状态流转

```
DRAFT → PLANNED → ACTIVE → FROZEN → PUBLISHED → CLOSED
          ↓
       CANCELLED
```

## 🛠️ 开发指南

### 后端开发

```bash
cd release-hub

# 构建
./mvnw clean install -DskipTests

# 运行
cd releasehub-bootstrap && ./mvnw spring-boot:run

# 运行测试
./mvnw test

# API 文档
open http://localhost:8080/swagger-ui.html
```

### 前端开发

```bash
cd release-hub-web

# 安装依赖
pnpm install

# 启动开发服务器
pnpm dev

# API 类型生成（后端 API 变更后）
pnpm gen:api

# 代码检查
pnpm lint && pnpm typecheck
```

### 测试

```bash
# 后端 Shell 测试
cd release-hub/tests/e2e
./run-all-tests.sh

# 前端 E2E 测试
cd release-hub-web
pnpm test:e2e
```

## 📋 规格驱动开发

本项目使用 OpenSpec 进行规格驱动开发：

```bash
# 查看进行中的变更
openspec list

# 验证提案
openspec validate --strict
```

详情参阅 [OpenSpec 指南](openspec/AGENTS.md)

## 🔗 相关链接

| 资源 | 链接 |
|------|------|
| 项目规划 | [docs/planning/PROJECT_PLAN.md](docs/planning/PROJECT_PLAN.md) |
| 领域模型 | [docs/architecture/DOMAIN_MODEL.md](docs/architecture/DOMAIN_MODEL.md) |
| API 文档 | [docs/api/RELEASE_WINDOW_API.md](docs/api/RELEASE_WINDOW_API.md) |
| 用户故事 | [docs/planning/USER_STORIES.md](docs/planning/USER_STORIES.md) |

## 📄 License

Private - All Rights Reserved
