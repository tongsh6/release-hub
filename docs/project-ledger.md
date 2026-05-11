# Project Ledger / 项目事实台账

> **新会话强制读取入口**：任何新会话 AI 进入本项目，必须先读本文件。
> 它替代不了 `docs/PROJECT_PROGRESS.md`（变更日志），但提供「事实基线」：
> 当前阶段在做什么、什么已经验证过、什么不要再做。
> 维护节奏：每个 release commit 必须更新「已验证 / Top Priority / 关键证据」三段。

---

## 1. 当前阶段目标

**v0.1.11 验证闭环 + 收口剩余 P0/P1**。
- 主线：让 v0.1.11 三大特性（远程版本更新 / 设置持久化 / 三层关联）+ 多 Provider 迁移 在真实 GitLab 上跑通并沉淀报告
- 不做：新功能（RBAC、通知、CI 深集成）；不重构 Iteration 领域到 `repoAssociations`

---

## 2. 已完成事项

| 事项 | 状态 | 证据路径 | 验证方式 | 备注 |
|---|---|---|---|---|
| ReleaseWindow CRUD + 状态流转 | 已实现 | `releasehub-application/...releasewindow` | 单测 + acc-v0.1.10 #7,12,17 | 闭环 |
| Iteration CRUD | 已实现 | `IterationAppService` | 单测 15 用例 | 闭环 |
| Iteration 三层关联 + BranchCreationMode | 已实现 | `BranchCreationMode.java` + V28 + IterationAppService.switch | 单测 + acc-v0.1.11 #10.1-10.5 | 闭环 |
| GitBranch 多 Provider 端口（Mock/GitHub/GitLab） | 已实现 | `GitBranchAdapterFactoryImpl` + 三 Adapter | 单测 + acc-v0.1.11 真实 API 调用 | GitLab 通过 `@ConditionalOnProperty(real-adapter=true)`；GitHub Adapter 默认装配（架构不对称） |
| Token AES-GCM 加密 | 已实现 | `GitTokenCrypto` + `GitTokenAttributeConverter` | 6 单测 + acc-v0.1.10 #4 | 闭环；新 DB 数据为空时 加密=0 是正常 |
| Attach Run 追踪 | 已实现 | `AttachAppService` + RunItem | acc-v0.1.10 修复表 | 闭环 |
| 冲突检测（7 种）| 已实现 | `ConflictDetectionAppService` | acc-v0.1.10 #11 + acc-v0.1.11 #5 | 闭环 |
| 远程版本更新 | 已实现 | commit `bbbae46` + `MavenVersionUpdaterAdapter` | 单测 | **未真实验证**（被 acc-v0.1.11 P1 阻塞） |
| 设置持久化（GitLab settings） | 已实现 | commit `55cb5b0` + `SystemSettingsJpaEntity` | 单测 | **未真实验证**（脚本未做重启回归） |
| GitLabRequest 反序列化修复 | 已实现 | commit `962be80`（@NoArgs/@AllArgs） | 单测 | 待脚本 v3.1 触达 POST /settings/gitlab |
| WindowLifecycleListener AFTER_COMMIT | 已实现 | commit `e1c5a31` | acc-v0.1.10 #13 | ⚠️ 异常未隔离引入新 P0（见第 4 节） |
| 验收脚本 v3 + wait_for_run + MOCK 模式降级 | 已实现 | commit `9eb5444` + `68381b1` | acc-v0.1.11 已使用 | 待 v3.1 补 ensure-settings 步骤 |

---

## 3. 已验证事项

| 事项 | 验证方式 | 报告路径 | 结论 |
|---|---|---|---|
| 全链路核心闭环 | 真实 GitLab 验收 v0.1.10 | `docs/reports/acceptance-v0.1.10-real-gitlab.md` | 20/20 PASS |
| v0.1.11 三层关联 5 场景 | 真实 GitLab 验收 v0.1.11 | `docs/reports/acceptance-v0.1.11-real-gitlab.md` | 10.1-10.5 全 PASS |
| 多 Provider 端口工厂路由 | 真实 GitLab 验收 v0.1.11 | 同上 | 后端日志显示 GitLab API 真实调用走 GitLabGitBranchAdapter |
| 单测基线 | `mvn test` | 本会话 2026-05-11 | 161 用例全过 |
| 前端 Vitest / typecheck | `npx vitest run` / `tsc --noEmit` | 2026-05-09 上次记录 | 18 / 0 错误 |

---

## 4. 进行中事项

| 事项 | 当前状态 | 阻塞点 | 下一步 |
|---|---|---|---|
| 闭掉 acc-v0.1.10 已知限制 ①「编排 0 items」 | 链路条件已具备（三层关联 + fallback 移除） | 被 acc-v0.1.11 P1「Settings missing」阻塞，未跑到该路径 | 修脚本 v3.1 后重跑 |
| 闭掉 acc-v0.1.10 已知限制 ②「VERSION_UPDATE FAILED」 | 远程版本更新已实现 | 同上 | 同上 |
| 验收脚本 v3.1（POST /settings/gitlab 一步） | 待实现 | — | 5 行代码追加在场景 2 末尾 |
| WindowLifecycleListener 异常隔离 | 待修 | — | `afterCommit` 中 BusinessException 仅 log 不 throw |

---

## 5. 已废弃事项

| 事项 | 废弃原因 | 决策证据 | 是否有残留 |
|---|---|---|---|
| `GitLabBranchPort` 旧端口 + Real/MockGitLabBranchAdapter + 测试 | 多 Provider 迁移完成后无人调用 | commit `0e2efb3`（2026-05-11） | 已清理 |
| Puppeteer E2E（历史 62 个） | 迁移到 Playwright（合并精简为 3 spec / 24 case） | PROJECT_PROGRESS.md 2026-05-09 注 | 已清理 |
| 「MVP」措辞 | 已脱离 MVP | commit `9e63a64` | 个别历史文档可能仍存在，非阻塞 |

> 已废弃事项**不得**重新作为新增实现建议，除非用户明确发起新需求。

---

## 6. 当前 Top Priority

| 优先级 | 事项 | 原因 | 验收标准 |
|---|---|---|---|
| **P0** | `WindowLifecycleListener` 异常隔离 | acc-v0.1.11 真实复现 `UnexpectedRollbackException`，污染日志 | Listener 中 BusinessException 不再向上抛 |
| **P1** | 验收脚本 v3.1 — POST /settings/gitlab | acc-v0.1.11 1 FAIL + 1 SKIP 都源于此 | 重跑达到 24/24 PASS |
| **P2** | 重跑验收，闭掉 v0.1.10 已知限制 ① ② | 「实现已超过验证」缺口尚未真正补齐 | acc-v0.1.11 报告升级为「通过」 |
| P3 | acc-v0.1.11 P2「release 分支 1/3」根因排查 | 真实数据观察项 | 找到 cloneUrl 重复前缀或 token 缺失根因 |

---

## 7. 关键证据索引

| 证据 | 路径 | 说明 |
|---|---|---|
| 最末验收报告 | `docs/reports/acceptance-v0.1.11-real-gitlab.md` | 22/24 PASS，含 P0/P1/P2/P3 真实问题清单 |
| 上轮验收报告 | `docs/reports/acceptance-v0.1.10-real-gitlab.md` | 20/20 PASS，含 2 处已知限制 |
| 验收脚本 | `scripts/acceptance/run-acceptance.sh` | v3，619 行，含 wait_for_run + MOCK 降级 |
| 种子初始化 | `scripts/e2e/init-gitlab.sh` | 幂等，3 个种子仓库 |
| 启动脚本 | `backend/scripts/run.sh` | `mvn spring-boot:run -pl releasehub-bootstrap` |
| 本地容器 | `releasehub-postgres`(5433) + `releasehub-gitlab`(9080) | 模式 A 常驻；端口策略见 memory `feedback_mode_a_b_port_isolation.md` |
| Flyway 最新迁移 | `V28__add_branch_creation_mode.sql` | local profile 下 flyway disabled，靠 `ddl-auto: update` |
| 测试基线 | `mvn test` | 161 通过（Surefire） |
| 项目变更日志 | `docs/PROJECT_PROGRESS.md` | 246 行，承担「变更日志」职责，事实基线请看本文件 |
| 需求索引 | `docs/requirements/INDEX.md` | 进行中：暂无；已完成已对齐 |

---

## 8. 与其它文档的关系

- **本文件 vs `PROJECT_PROGRESS.md`**：本文件是「事实台账」（当前是什么），PROGRESS 是「变更日志」（变了什么）
- **本文件 vs `requirements/INDEX.md`**：INDEX 关注需求生命周期；本文件关注**实现 vs 验证**的差额
- **本文件 vs `openspec/changes/*/tasks.md`**：tasks.md 关注单个 change 的细粒度复选框；本文件提供项目级视图
- **维护规则**：每次发版（v0.1.x）的 commit 必须同时改本文件第 2/3/6/7 节，否则会重新漂移
