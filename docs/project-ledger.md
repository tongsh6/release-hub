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
| GitLabRequest 反序列化修复 | 已实现 | commit `962be80`（@NoArgs/@AllArgs） | 单测 + acc-v0.1.11 第 1 轮 POST /settings/gitlab 成功 | 闭环 |
| WindowLifecycleListener AFTER_COMMIT + 异常隔离 | 已实现 | commit `e1c5a31` + 本会话 commit | acc-v0.1.10 #13 + acc-v0.1.11 终轮 0 次 UnexpectedRollback | 闭环 |
| 验收脚本 v3.2（含 ensure-settings + token 刷新 + 冲突识别） | 已实现 | commit `9eb5444` + `68381b1` + 本会话 | acc-v0.1.11 第 3 轮 25/26 PASS | 闭环 |
| GitLabGitBranchAdapter URL 双重 encode 修复 | 已实现 | 本会话（uri(...) 包装 + ENC 测试同步） | acc-v0.1.11 终轮 release 分支 3/3、listBranches 18 个 | 闭环 |

---

## 3. 已验证事项

| 事项 | 验证方式 | 报告路径 | 结论 |
|---|---|---|---|
| 全链路核心闭环 | 真实 GitLab 验收 v0.1.10 | `docs/reports/acceptance-v0.1.10-real-gitlab.md` | 20/20 PASS |
| v0.1.11 全链路 + 三层关联 + 多 Provider + 设置持久化 | 真实 GitLab 验收 v0.1.11 终轮 | `docs/reports/acceptance-v0.1.11-real-gitlab.md` | **25 PASS / 0 FAIL / 1 SKIP**（SKIP 为业务正确拒绝） |
| URL 双重 encode 修复连带 release 分支创建 | 同上场景 4 | 同上 | 1/3 → **3/3** |
| Listener 异常隔离 | 同上后端日志 | 同上 | UnexpectedRollback 出现次数 2 → **0** |
| 单测基线 | `mvn test` | 本会话 2026-05-11 | 161 用例全过（含 GitLabGitBranchAdapterTest ENC 同步纠正） |
| 前端 Vitest / typecheck | `npx vitest run` / `tsc --noEmit` | 2026-05-09 上次记录 | 18 / 0 错误 |

---

## 4. 进行中事项

> 暂无（2026-05-11 23:46 起）。v0.1.11 终轮验收 25/26 PASS（1 SKIP 为业务正确拒绝），
> 所有阻塞 P0/P0+/P1 全部下线。下一波 Top 由用户驱动。

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

> 暂无强制 P0/P1（2026-05-11 23:46）。下方为可选改进，按出现顺序由用户决定何时推进。

| 优先级 | 事项 | 原因 | 验收标准 |
|---|---|---|---|
| 可选 | 累积冲突清理脚本 | 验收幂等 + 累积造成 14 个真实分支冲突，clean-room 路径不可重现 | 一键 reset 仓库到只剩 main + seed feature 分支 |
| 可选 | 前端 Playwright E2E 真实跑一次 | 3 spec / 24 case 历史报告未在本会话刷新 | 24/24 通过 |
| 可选 | acc-v0.1.10 报告中段移到 archive | 已被 v0.1.11 报告完全覆盖 | reports/ 目录瘦身 |

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
