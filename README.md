# ReleaseHub

**发布窗口驱动的跨仓分支与集成编排系统（GitLab）**：一个窗口关联多个迭代，迭代关联可重叠的仓库，按仓库将所选 feature 分支依次集成到窗口 release 分支。

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-green.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5+-blue.svg)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9+-blue.svg)](https://www.typescriptlang.org/)

核心能力：窗口/迭代/仓库与绑定、权威计划预览、显式 MR-first 编排、Run/Item/Step 与持久任务证据，以及按原计划精确重试。Attach/Detach/Publish 只维护配置和生命周期；Orchestrate 将所选 feature 分支集成到窗口 release 分支。

当前 WINDOW 编排先在事务中保存计划与任务，HTTP 202 返回 Run ID；有界后台 worker 只消费已落库的 ENSURE_FEATURE → ENSURE_RELEASE → ENSURE_MR → TRY_MERGE 四动作。执行和重试必须提供 `Idempotency-Key`；相同主体、键及请求返回原回执，后台沿冻结计划和已确认 GitLab 数值项目 ID 推进。进程重启可继续尚未领取的 PENDING。C2-B1 已完成验收并交付：仅接管期限已到、领取事实完整，且该 task 没有任何 git_operation 或自身 Step 的 RUNNING；Run→Task 锁内重核并按原 token CAS，保留首次领取时间及已有实际顺序，缺失顺序不补造。B1 本身不恢复已有写意图；C2-B2 已交付建分支结果投影，C2-B3 的 MR 创建检查点恢复已完成本地与固定 SHA 验收并交付。C2-B4 合并 MR 完整证明与原结果恢复已通过本地及固定 SHA CI 验收并交付；C2-B5 已实现完整、唯一且尚未发送的 MERGE INTENT 安全续发：只更换 Task 当前领取权，原操作和预留归属保留；已 SEND 或结果未知的操作仍保持保护。B5 本地验收与固定后端提交的远端 CI 5/5 均通过，已分组推送。

关闭窗口使用独立的仓库级收尾 Run；重复关闭返回原回执，Tag 使用确认的合并提交，CI 默认关闭并由用户显式选择，共享分支与迭代保留。C1 固定 `e5094ba` 的 [Full-Link CI](https://github.com/tongsh6/release-hub/actions/runs/44899945002) 为 5/5。**S3-C2-A 已完成并分组推送**：202、后台扫描、回执找回和进度观察通过本地门禁，固定 `1761572` 的 [Full-Link 44905843754](https://github.com/tongsh6/release-hub/actions/runs/44905843754) 为 5/5。**S3-C2-B1 已完成并推送**：后端 `3f810c8`、文档 `5441df6`，固定 `5441df6bdc971953dee390c9470d4786fe0eab44` 的 [Full-Link 44911634004](https://github.com/tongsh6/release-hub/actions/runs/44911634004) 为 5/5。

**S3-C2-B2 已完成并分组推送**：建分支成功证明 `fe4c006`、原任务投影恢复 `1386ccc`、交付记录 `cc80036`；固定 `cc800362f333418776f4a816e1fa74ee9f08c04f` 的 [Full-Link 44917254215](https://github.com/tongsh6/release-hub/actions/runs/44917254215) 为 5/5。同一源码树的组合 189/189、API 48/48、普通后端 695 PASS/5 既有 SKIP 和静态扫描通过，详细证据见[本轮验收记录](tasks/records/2026-11-21-sa-013-orchestration-consistency.md)。

该片修复了其他 SHA 字段覆盖 `commit.id` 而错误确认建分支成功的问题。成功证明统一为 HTTP 201、匹配的文本 name 和确切 commit.id；进程在远端结果已落库、原任务结果未提交时中断，后台可只补原任务记录。实际强杀/重启验证中，投影阶段 HTTP 请求为 0、建分支 POST 总数为 1，原 token、期限及历史事件保持。

C2-B2 的范围仅为 ENSURE_FEATURE/ENSURE_RELEASE 的 CREATE_BRANCH/APPLIED。**S3-C2-B3 已完成并分组推送**：MR 创建需完整 HTTP 201、同仓项目身份、分支、IID/URL、冻结源 SHA 和 opened 证明；正常执行与中断恢复共用判定，原 ENSURE_MR 检查点可只在数据库事务内补齐。后继 TRY_MERGE 仍消费原 IID/SHA 并复核当前状态；创建不产生合并提交证据。

最终定向 403/403、API 48/48、普通后端 738 项（733 PASS/5 既有 SKIP）及静态扫描通过，七份 backend hash 快照一致。真实强杀/重启中，补检查点阶段 HTTP=0，整个场景 MR POST=1、MERGE PUT=1。后端 `d87dfa8`、验收文档 `544983b` 已推送，固定 `544983beff74604852c050209e4c8caa93678ef2` 的 [Full-Link 44924378741](https://github.com/tongsh6/release-hub/actions/runs/44924378741) 为 5/5。详见[验收记录第 16 节](tasks/records/2026-11-21-sa-013-orchestration-consistency.md)。

**S3-C2-B4 已完成并分组推送**：完整 HTTP 200/merged 证明与原 IID/source SHA 绑定，允许合法的显式 null 合并 SHA；末尾任务没有 PENDING 后继时也能被发现，并在原结果事务中补历史 MERGED，保持原检查点身份及操作证据。最终定向 533/533、API 48/48、普通 789 项（784 PASS/5 既有 SKIP）和静态扫描通过。真实进程强杀/重启后恢复阶段 HTTP=0，全场景 MR POST=1、MERGE PUT=1。后端 `23950f9`、文档 `be1ccbe` 已推送，固定 `be1ccbe61a5155ba5ce8582f884d41dbb385a932` 的 [Full-Link 44929301318](https://github.com/tongsh6/release-hub/actions/runs/44929301318) 为 5/5。详见[验收记录第 17 节](tasks/records/2026-11-21-sa-013-orchestration-consistency.md)。

**S3-C2-B5 已完成并分组推送**：沿原执行路径核对完整 typed 请求、稳定指纹和当前 Task 权限，再复用原操作发送一次 PUT；旧 worker 不能发送或覆盖结果。相关 13 套 / 692 项通过（本轮受影响 2 套 328 项，加源码未变的 11 套 364 项，非单次整跑），API 48/48、普通 816 项（811 PASS/5 既有 SKIP）与静态门禁通过。真实进程强杀后续发一次；APPLIED 后再次强杀，第三个 JVM 零 HTTP 补齐原结果。

后端 [`064282887ae825255360bcf3b04d0377163219aa`](https://github.com/tongsh6/release-hub/commit/064282887ae825255360bcf3b04d0377163219aa) 与文档 `62d075c36bd96c35d14bc541957787c5173559f8` 已分阶段推送，固定该后端 SHA 的 [Full-Link 44935216788](https://github.com/tongsh6/release-hub/actions/runs/44935216788) 为 5/5，B5 已 DONE。完整 RED/GREEN、源码绑定及失败路径见[验收记录第 18 节](tasks/records/2026-11-21-sa-013-orchestration-consistency.md)。

当前按[执行路线图](docs/execution-roadmap.md)接续 B6「原 MERGE 未发送意图的本地结果收口」，已完成独立 RED→GREEN 与本地门禁，仍为唯一 HEAD。锁内核完整未发送证明后，当前 Task 结果、原操作 NOT_SENT 与匹配 guard 释放同事务完成；已开始 SEND 的操作拒绝提前 UNKNOWN，保留真实响应处理权。业务 UNKNOWN 仍阻断同仓后继及普通/自动重试。相关单次 749/749、API 48/48、普通 816 项（811 PASS/5 既有 SKIP）、前端 217/217 和静态通过，详见[验收记录第 19 节](tasks/records/2026-11-21-sa-013-orchestration-consistency.md)。实现 [`4e992e9c2406586ec23fa7636292e3d48c163118`](https://github.com/tongsh6/release-hub/commit/4e992e9c2406586ec23fa7636292e3d48c163118) 已分阶段推送，固定该完整 SHA 的 [Full-Link 44940092094](https://github.com/tongsh6/release-hub/actions/runs/44940092094) 已启动、仍在执行中；其他 INTENT、SEND/UNKNOWN 与完整 UI 黄金旅程仍独立验收。

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
