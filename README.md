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

**S3-C2-B6 已完成并分阶段推送**：锁内核完整未发送证明后，当前 Task 结果、原操作 NOT_SENT 与匹配 guard 释放同事务完成；已开始 SEND 的操作拒绝提前 UNKNOWN，保留真实响应处理权。业务 UNKNOWN 仍阻断同仓后继及普通/自动重试。相关单次 749/749、API 48/48、普通 816 项（811 PASS/5 既有 SKIP）、前端 217/217 和静态通过。实现 [`4e992e9c2406586ec23fa7636292e3d48c163118`](https://github.com/tongsh6/release-hub/commit/4e992e9c2406586ec23fa7636292e3d48c163118)、文档 `29ec00586be20730e66cf37da12614e6450d240d` 已推送，固定该实现完整 SHA 的 [Full-Link 44940092094](https://github.com/tongsh6/release-hub/actions/runs/44940092094) 为 5/5，B6 已 DONE，详见[验收记录第 19 节](tasks/records/2026-11-21-sa-013-orchestration-consistency.md)。

**S3-C2-B7 已完成验收，实现已推送**：feature/release 沿原冻结 SHA 和数值项目接续完整未发送意图，并原子收口真实 NOT_SENT；sourceRef 前进/删除不重新求值，项目重映射在预检前拒绝，SEND 无响应时保留真实发送者处理权。独立 RED 后定向 91/91、单次相关 840/840、API 48/48、普通后端 831（826 PASS / 5 既有 SKIP）、静态与源码绑定均通过。实现 `00def779f82d4b10b13c547bacda08fd91706fc9` 已推送；[固定实现 SHA 的 Full-Link 44945748427](https://github.com/tongsh6/release-hub/actions/runs/44945748427) attempt 2 为 5 PASS/0 FAIL，B7 已 DONE。首轮 Docker 依赖下载 Central 429 与重跑全过程保留于[验收记录第 20 节](tasks/records/2026-11-21-sa-013-orchestration-consistency.md)，验收文档单独收口；该验收限后端 API 与隔离 GitLab，不扩大完整 UI 旅程。

B8「正常 CREATE_MR 完整请求的发送证明闭环」已验收：正常 ENSURE_MR 与已有非 Task cleanup 均验证已接纳的完整命令确实进入 HTTP。独立新例 125/125、相关回归 965/965、API 48/48、普通后端 872 PASS / 5 既有 SKIP 及静态通过；实现 `537588e` 已推送，固定提交的 [Full-Link CI](https://github.com/tongsh6/release-hub/actions/runs/44972473174) 为 5 PASS / 0 FAIL。

**B9 已完成验收并推送实现。** B9 原 CREATE_MR INTENT 的安全接续与本地收口已验收：复用原项目 I、SHA A、完整请求及 operation/attempt，换权只更新当前 Task 发送资格；真实 noSEND 结果与原 NOT_SENT/Task/Step/metadata/Run/guard 同事务收口。原 SEND/UNKNOWN 继续保留保护，步骤读取复用匹配 Task 的持久顺序。独立正反例先行，最终单次 13 suites 1078/1078（含 113 新例）、API 48/48、普通 918（913 PASS / 5 既有 SKIP）及静态通过；十份 495 文件最终快照相同。实现 `666acdc7a60c0922db2f05713cbfefd23eed8265` 已推送，固定该 SHA 的 [Full-Link 44978661003](https://github.com/tongsh6/release-hub/actions/runs/44978661003) attempt 1 为 5 PASS / 0 FAIL。证据见[验收记录第 22 节](tasks/records/2026-11-21-sa-013-orchestration-consistency.md)。

**B10 已完成验收并推送实现。** B10「MERGE_MR 精确只读核对证据」已验收：原 PUT 定位、实际 GET 状态与完整 I/IID/A 证明共同决定观察，十字段保留 JSON 类型和显式 null；原 Task/Step/guard/seq2 保持。冻结 RED 为 23 FAIL/4 正控 PASS，同批 27/27 GREEN；最终单次相关 1123/1123、API 48/48、普通 942（937 PASS/5 既有 SKIP）、前端 218/218及静态通过。实现 `8dcca5bf8265a3a1f36c16a14f0e3b8b0b35652d` 已推送，固定该 SHA 的 [Full-Link 44982102777](https://github.com/tongsh6/release-hub/actions/runs/44982102777) attempt 1 为 5 PASS/0 FAIL。 证据见[验收记录第23节](tasks/records/2026-11-21-sa-013-orchestration-consistency.md)。

**B11 已完成验收并推送实现。** B11「GitLab MR 读取响应状态证明」已验收：getMap/readPages 仅让实际 HTTP 200 进入原正文与身份校验，正常返回的非 200 为 INCOMPLETE；分页任一页不合格即舍弃已累积候选，既有异常分类保持。冻结独立 RED 为 19 FAIL/14 正控 PASS，同批 33/33 GREEN（28 新例及 5 既有正控）；最终单次相关 1151/1151、API 48/48、普通 967（962 PASS/5 既有 SKIP）及静态通过。实现 `35ca21366ec9124fe9c4d6d2ef162a09c13709fb` 已推送，固定该 SHA 的 [Full-Link 44984865795](https://github.com/tongsh6/release-hub/actions/runs/44984865795) attempt 1 为 5 PASS/0 FAIL。 证据见[验收记录第24节](tasks/records/2026-11-21-sa-013-orchestration-consistency.md)。

**B12 已完成验收并推送实现。** B12「无自身写意图任务的失败与重试闭环」已验收：在原Run→Task锁与token核准下，仅将当前Task无任何自身operation且无自身Step的首次UNKNOWN归FAILED，同一结果贯穿原结果事务；诊断、原计划及真实未知写保护保持，现新Run retry继续遵守源Run结束等受理门禁。独立23新例为11 FAIL/12正控PASS→23/23 GREEN；单次相关1174/1174含这23例，API旧fixture补证后48/48、普通967（962 PASS/5旧SKIP）及静态通过。实现 `7042f19278ff8d8bfa102a8f27d0c1f1b949539d` 已推送，固定该SHA的 [Full-Link 44989857158](https://github.com/tongsh6/release-hub/actions/runs/44989857158) attempt 1为5 PASS/0 FAIL。 证据见[验收记录第25节](tasks/records/2026-11-21-sa-013-orchestration-consistency.md)。

B13「项目身份响应证明统一」已DONE：三入口共用actual200/object/正整数JSON id无损String证明，保留各入口定位、凭据、异常政策及long消费边界。冻结独立70例为21 FAIL/49 PASS→70/70 GREEN；单次相关1239/1239含该70例，API48/48、普通1019（1014 PASS/5既有SKIP）及真实静态门禁通过。业务实现`4dbe2c6fd17b0f02a7306dcb7549c2fa83b2d7b5`、静态门禁修复`ddcec2ea6520ae7b347e13f9a2a28d1fae68bb67`已分组推送；固定后者完整SHA的[Full-Link 45102738088](https://github.com/tongsh6/release-hub/actions/runs/45102738088) attempt 1已completed/success，5 PASS/0 FAIL。完整证据、首次静态假PASS纠正及8条既有warning处置见记录第26节。

B14「原 MERGE_MR 已持久拒绝响应的结果投影」已DONE：七个生产文件接通原证明、默认扫描与原结果事务，恢复零HTTP；冻结140例（137新+3旧）为37 FAIL/103 PASS→140/140，单次相关1376、API48、普通1019（1014 PASS/5既有SKIP）及静态同树通过。实现`36d6c7b0a692ada9ae77b484967e58df87909aac`已推送，固定该SHA的[Full-Link 45115321412](https://github.com/tongsh6/release-hub/actions/runs/45115321412) attempt 1为5 PASS/0 FAIL。新增独立断言保持，既有候选集合预期适配与完整证据见记录27.6–27.12。SEND/UNKNOWN契约拆分已完成；当前唯一HEAD为B15「MERGE_MR同步200证据不足的UNKNOWN审计式恢复」，事前契约见OpenSpec的unknown-merge-recovery.md和记录28。本轮仅完成契约与只读源码核查，B15实现及行为测试尚未开始；真正I/O未确认、其他来源/动作和完整跨场景UI继续独立验收。任务以[执行路线图](docs/execution-roadmap.md)为准。

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
