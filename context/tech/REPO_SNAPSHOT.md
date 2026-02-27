# ReleaseHub 仓库快照

> AIEF L0+ 核心制品 — 面向 AI 快速理解的高层仓库概览。
> 详细后端审计见 [context/experience/reports/backend-structure.md](../experience/reports/backend-structure.md)。

## Tech Stack

| 层 | 技术 | 版本 |
|----|------|------|
| 语言 | Java | 21 |
| 后端框架 | Spring Boot | 3.4.1 |
| 前端框架 | Vue | 3.5+ |
| 构建工具（前端） | Vite (rolldown-vite) | 7.x |
| 数据库 | PostgreSQL | 18.1 |
| ORM / 迁移 | JPA + Flyway | 10.20.0 |
| UI 组件库 | Element Plus | 2.12+ |
| 包管理 | Maven (后端) / pnpm (前端) | — |

## Repo Layout

```
releasehub/
├── release-hub/            # 后端 Maven 多模块项目
├── release-hub-web/        # 前端 Vue 3 项目
├── context/                # 知识库（业务/技术/经验）
├── openspec/               # 规范驱动开发（提案/规范/归档）
├── requirements/           # 需求管理
├── workflow/               # 工作流阶段定义
├── .ai/                    # AI 三层架构（agents/commands/skills）
├── .ai-adapters/           # 多工具适配层
├── scripts/                # 开发/运维脚本
├── docker-compose.yml      # 本地开发环境
├── AGENTS.md               # AI 编程指南（项目记忆入口）
└── README.md               # 项目说明
```

## Modules / Services

### 后端（6 模块，DDD + 模块化单体）

| 模块 | 职责 |
|------|------|
| `releasehub-common` | 通用工具、常量 |
| `releasehub-domain` | 聚合根、实体、值对象（**无外部依赖**） |
| `releasehub-application` | 用例编排、事务边界、Port 接口定义 |
| `releasehub-infrastructure` | JPA 实现、外部适配器（GitLab 等） |
| `releasehub-interfaces` | REST 控制器、DTO |
| `releasehub-bootstrap` | Spring Boot 启动入口 |

**依赖规则**：domain ← application ← infrastructure ← interfaces ← bootstrap

### 前端（1 项目）

| 项目 | 技术栈 | 入口 |
|------|--------|------|
| `release-hub-web` | Vue 3 + TypeScript + Element Plus | `pnpm dev` |

## Infra & CI

### 本地开发

- **docker-compose.yml**：PostgreSQL 18.1（端口 5432，数据库 `release_hub`）

### CI/CD（GitHub Actions）

- **ci.yml** 触发：push / PR → main
  - `requirements-gate`：需求门禁校验
  - `backend`：Java 21 + Maven 全量测试
  - `frontend`：Node 18 + pnpm（typecheck → lint → test → build）

## Commands

```bash
# === 后端 ===
cd release-hub

# 构建
./mvnw clean package -DskipTests

# 测试
./mvnw -q clean test

# 运行
./mvnw spring-boot:run -pl releasehub-bootstrap

# === 前端 ===
cd release-hub-web

# 安装依赖
pnpm install

# 开发
pnpm dev

# 构建
pnpm build

# 测试
pnpm test

# 类型检查 + Lint
pnpm typecheck && pnpm lint

# E2E 测试
pnpm test:e2e

# 从后端 OpenAPI 生成 API 类型
pnpm gen:api

# === 基础设施 ===
# 启动数据库
docker compose up -d

# 全量验证
scripts/dev/verify-all.sh
```
