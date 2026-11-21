# ReleaseHub

**发布窗口驱动的跨仓分支与集成编排系统（GitLab）**：一个窗口关联多个迭代，迭代关联可重叠的仓库，按仓库将所选 feature 分支依次集成到窗口 release 分支。

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-green.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5+-blue.svg)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9+-blue.svg)](https://www.typescriptlang.org/)

核心能力：窗口/迭代/仓库与绑定、权威计划预览、显式 MR-first 编排、Run/Item/Step 与持久任务证据，以及按原计划精确重试。Attach/Detach/Publish 只维护配置和生命周期；Orchestrate 将所选 feature 分支集成到窗口 release 分支。

当前 WINDOW 编排由请求线程消费已落库的 ENSURE_FEATURE → ENSURE_RELEASE → ENSURE_MR → TRY_MERGE 四动作任务，沿用冻结计划并核对已确认的 GitLab 数值项目 ID。执行和重试必须提供 `Idempotency-Key`；同一主体、同一键及同一请求返回原 Run，不重新驱动执行。MR 检查点与任务结果可查询；结果未知时保留仓库阻断并要求核对。

关闭窗口使用独立的仓库级收尾 Run；重复关闭返回原回执，Tag 使用确认的合并提交，CI 默认关闭并由用户显式选择，共享分支与迭代保留。当前 S3-C1 本地候选已通过真实数据库、本地 HTTP fixture、进程硬杀、前端回归和最终静态扫描；修复后新 SHA 的 Full-Link CI 待完成。真实 UI 黄金旅程仍需单独验收，不能用后端测试替代。后台 worker、202/轮询、租约接管和自动恢复属于后续 S3-C2，当前不会自动继续中断执行。

> **协作入口：[`AGENTS.md`](AGENTS.md)** — 唯一项目画像、工程规则与知识导航；生命周期、工作模式和发布事实分别记录。
> 📋 **实现与验证事实：[`docs/project-ledger.md`](docs/project-ledger.md)** — 已交付基线、当前任务目标与验收边界。
> 🎯 **需要挑任务时读取：[`docs/execution-roadmap.md`](docs/execution-roadmap.md)** — 当前唯一队首任务与后续执行队列。

## 项目结构

```
release-hub/
├── backend/          # Spring Boot 后端（六边形架构）
├── frontend/         # Vue 3 + TypeScript + Vite 前端
├── deploy/           # 开源单机 Docker Compose 部署包
└── docs/             # 设计文档、架构决策、工作流规范
```

## 快速开始

```bash
# 启动后端（Mock Git 适配器，本地开发安全）
cd backend && ./scripts/run.sh

# 启动后端（真实 GitLab 适配器，需要先配置 GitLab 连接）
cd backend && SPRING_PROFILES_ACTIVE=local,real ./scripts/run.sh

# 启动前端
cd frontend && corepack pnpm install --frozen-lockfile && corepack pnpm dev
```

> **适配器模式**：默认 Mock 模式不访问真实 Git 平台，用于本地配置页面开发；真实编排须叠加 `real` profile 并配置 GitLab。真实 HTTP 写入保护默认开启；显式关闭保护时写请求被拒绝，不回退为无保护执行。模式配置见 [部署指南](docs/deployment.md#23-git-平台适配器模式重要)，当前验收边界见下方研发记录。

前端使用 `.nvmrc` 声明的 Node 24 主版本及 `frontend/package.json` 固定的 pnpm 10.33.0。CI 和两个前端 Docker 构建入口读取同一 pnpm 版本声明；已有依赖无需重复安装。详见 [前端开发指南](frontend/README-DEV.md)。

## 自托管部署

开源用户推荐使用源码 Docker Compose 部署：

```bash
cp deploy/compose/.env.example deploy/compose/.env
$EDITOR deploy/compose/.env
docker compose --env-file deploy/compose/.env -f deploy/compose/docker-compose.yml up -d --build
deploy/scripts/healthcheck.sh
```

该方式在单台服务器运行前端、后端和 PostgreSQL，只连接用户已有的外部 GitLab。完整说明见 [Docker Compose 源码部署](docs/deploy/docker-compose.md)，升级流程见 [版本升级与回滚](docs/deploy/upgrade.md)。

首次空库部署必须在 `.env` 中设置 `RELEASEHUB_BOOTSTRAP_ADMIN_PASSWORD`，然后使用配置的用户名和密码登录。生产环境不提供默认密码；已有账户不会因初始化配置变化而重置。

生产部署还必须设置并独立备份 `RELEASEHUB_CRYPTO_SECRET_KEY`。新实例使用 Base64 编码的 32 字节随机密钥；已有加密实例必须沿用原密钥。数据库备份不包含该密钥。

## 文档

- [Docker Compose 源码部署](docs/deploy/docker-compose.md) — 开源用户单机部署入口
- [项目画像](docs/context/PROJECT_PROFILE.yaml) — 已确认生命周期、项目约束与未知责任
- [AIEF 接入与阶段核对](tasks/records/2026-11-21-aief-project-operating-context.md) — 上游来源、代码/历史证据和专项验证
- [架构文档](docs/context/tech/architecture/) — 系统架构与技术栈
- [开发规范](docs/context/tech/conventions/) — 代码规范与测试策略
- [执行路线图](docs/execution-roadmap.md) — 当前唯一队首任务
- [当前编排研发记录](tasks/records/2026-11-21-sa-013-orchestration-consistency.md) — 手动编排、状态/重试、默认写入保护、验收与未完成边界
- [S2 历史交付](docs/openspec/changes/add-durable-run-recovery/s2-implementation.md) — 2026-10-08 的操作账本、核对 API 与隔离 GitLab 证据；配置以当前研发记录为准
- [工作流](docs/workflow/) — AI 辅助开发工作流
- [API 契约](backend/docs/architecture.md) — REST API 概览

## 技术栈

| 端 | 技术 |
|---|------|
| 后端 | Java 21, Spring Boot 3.4, Maven 3.9+, PostgreSQL, Flyway |
| 前端 | Vue 3, TypeScript, Vite, pnpm |
| 测试 | JUnit 5, Testcontainers, ArchUnit, Vitest, Playwright |
