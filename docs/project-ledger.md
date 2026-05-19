# Project Ledger / 项目事实台账

> **新会话强制读取入口**：任何新会话 AI 进入本项目，必须先读本文件。
> 它替代不了 `docs/PROJECT_PROGRESS.md`（变更日志），但提供「事实基线」：
> 当前阶段在做什么、什么已经验证过、什么不要再做。
> 维护节奏：每个 release commit 必须更新「已验证 / Top Priority / 关键证据」三段。

---

## 1. 当前阶段目标

**v0.1.11 验证闭环 + 场景矩阵驱动收口**。
- 主线：按 `docs/reports/scenario-acceptance-matrix.md` 推进用户旅行场景地图，把 SA-012/SA-013/SA-014/SA-016 已闭环证据沉淀为基线，并优先补 SA-015/SA-012/SA-010/SA-011 剩余缺口
- 不做：新功能（RBAC、通知、CI 深集成）；不重构 Iteration 领域到 `repoAssociations`

### 场景化用户旅程自动化原则（AI 接手必读）

- ReleaseHub 业务数据必须由前端用户旅程创建或变更：分组、仓库纳管、迭代、发布窗口、挂载、冲突扫描、冲突解决、发布编排、版本更新等，不得用 API 或数据库脚本偷造前置业务状态后再声称完成用户旅程。
- 外部环境 fixture 可以由脚本准备：GitLab/Postgres/Backend/Frontend 服务、GitLab seed repo、GitLab PAT、基础容器状态。这些不是 ReleaseHub 用户在系统内完成的业务动作。
- API、数据库、GitLab 查询只能作为证据复核：用于证明页面动作后的后端约束、Run 记录、分支、提交、版本写回成立，不能替代前端点击路径。
- 历史存量数据只能用于“可见性/复核既有记录”场景；SA-012/SA-013/SA-014 这类“用户触发并完成动作”的黄金路径，必须从页面完成关键动作。
- 自动化测试如果需要已知起点，必须在同一个 serial Playwright 旅程中通过 UI 建立，或明确声明它验证的是外部 fixture/存量可见性，而不是完整业务用户旅程。

可执行入口：

- 本地前后端统一托管入口：`scripts/dev/start-local-env.sh hold`
- 本地前后端状态检查/停止入口：`scripts/dev/start-local-env.sh status` / `scripts/dev/start-local-env.sh stop`
- 后端/真实 GitLab 场景验收入口：`bash scripts/acceptance/run-acceptance.sh`
- Slice-1 分组/发布窗口旅程：`cd frontend && pnpm run test:e2e:slice-1`
- 完整前端用户旅程基线：`cd frontend && pnpm run test:e2e`
- 测试源码入口：`frontend/e2e/tests/slice-1-group-window.spec.ts`、`frontend/e2e/tests/slice-2-full-flow.spec.ts`

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
| 远程版本更新 | 已验证 | commit `bbbae46` + `MavenVersionUpdaterAdapter` + `run-acceptance.sh` SA-014 | 单测 + 真实 GitLab 验收 | Maven 单模块 release 分支 commit 已验证 |
| 设置持久化（GitLab settings） | 已验证 | commit `55cb5b0` + `SystemSettingsJpaEntity` + `run-acceptance.sh` SA-001/SA-004 | 单测 + 后端重启验收 | 重启后 settings 和仓库数据可查询 |
| GitLabRequest 反序列化修复 | 已实现 | commit `962be80`（@NoArgs/@AllArgs） | 单测 + acc-v0.1.11 第 1 轮 POST /settings/gitlab 成功 | 闭环 |
| WindowLifecycleListener AFTER_COMMIT + 异常隔离 | 已实现 | commit `e1c5a31` + 本会话 commit | acc-v0.1.10 #13 + acc-v0.1.11 终轮 0 次 UnexpectedRollback | 闭环 |
| 验收脚本 v3.2（含 ensure-settings + token 刷新 + 冲突识别） | 已实现 | commit `9eb5444` + `68381b1` + 本会话 | acc-v0.1.11 第 3 轮 25/26 PASS | 闭环 |
| GitLabGitBranchAdapter URL 双重 encode 修复 | 已实现 | 本会话（uri(...) 包装 + ENC 测试同步） | acc-v0.1.11 终轮 release 分支 3/3、listBranches 18 个 | 闭环 |
| 场景化验收矩阵基线 | 已验证 | `docs/reports/scenario-acceptance-matrix.md` + `scripts/acceptance/run-acceptance.sh` | 2026-05-20 真实 GitLab 验收 | PASS=134 / FAIL=0 / SKIP=0；SA-011 MERGE_CONFLICT、CROSS_REPO_VERSION_MISMATCH、REPO_AHEAD、SYSTEM_AHEAD、GIT_PERMISSION_DENIED、GIT_UNAVAILABLE 后端/GitLab 强证据已补；SA-015/SA-016 真实部分失败重试强证据已补 |
| SA-011 版本领先风险前端观察 | 已验证 | `frontend/e2e/tests/slice-2-full-flow.spec.ts` | 2026-05-18 Slice-2 Playwright | `REPO_AHEAD` / `SYSTEM_AHEAD` 类型分布、版本差异、阻断级别、建议处理方式和 `USE_SYSTEM` 同步请求语义已覆盖 |
| SA-011 Git 访问异常风险闭环 | 已验证 | `ConflictType` + `ConflictDetectionAppService` + `ConflictPanel.vue` + `run-acceptance.sh` 5.9 + Slice-2 Playwright | 后端单测 + adapter 单测 + Vitest + typecheck/i18n + 真实 GitLab 验收 + Playwright | 新增 `GIT_PERMISSION_DENIED` / `GIT_UNAVAILABLE`，避免权限/不可达被吞掉、冒泡或误判为普通合并冲突；真实 GitLab 权限不足/不可达和前端用户旅程证据已补 |
| 前端 SA-012/SA-013/SA-014 用户触发旅程 | 已验证 | `frontend/e2e/tests/slice-2-full-flow.spec.ts` | 2026-05-14/15 真实前后端联调 + 前端请求证据 | UI 创建业务数据后完成版本冲突解决、分支名不合规和 release 分支已存在外部处理观察、编排和版本更新触发；最终请求体断言作用域正确 |
| 前端 Playwright E2E 基线 | 已验证 | `frontend/e2e/tests` | 2026-05-15 真实前后端联调 | 29 PASS / 0 FAIL / 0 SKIP；历史显式 skip 已转为可执行旅程 |
| 本地环境统一启停脚本 | 已验证 | `scripts/dev/start-local-env.sh` | 2026-05-15 真实前后端联调 | `start|hold|stop|restart|status` 可用；`hold` 托管前后端；前端 `/api` 代理登录 200 |
| SA-016 发布后收尾闭环 | 已验证 | `scripts/acceptance/run-acceptance.sh` + `frontend/e2e/tests/slice-1-group-window.spec.ts` | 2026-05-15 真实 GitLab 验收 + Playwright | 关闭窗口、重复关闭幂等、关闭后挂载/版本更新拒绝、收尾 Run 可见、前端 CLOSED 窗口隐藏挂载入口 |
| SA-016 发布窗口报告导出 | 已实现 | `ReleaseWindowReportController` + `ExportAppService` + `ReleaseWindowDetail.vue` | MockMvc + Vitest + typecheck/i18n | 窗口维度 JSON/CSV 汇总 window、Run、RunItem、RunStep、结果分布；详情页可导出 CSV 报告 |
| SA-010 解除挂载 UI E2E 复核 | 已验证 | `frontend/e2e/tests/slice-1-group-window.spec.ts` | 2026-05-19 Slice-1 Playwright | UI 创建迭代、挂载到窗口、详情页解除挂载、`detach` 请求成功、关联列表为空和发布计划隐藏 |
| Maven surefire/failsafe 插件版本显式化 | 已验证 | `backend/pom.xml` | `mvn -pl releasehub-bootstrap -DskipTests validate` + `mvn -pl releasehub-application -Dtest=ConflictDetectionAppServiceTest test` | malformed POM 中插件版本缺失警告已关闭 |
| acc-v0.1.10 验收报告归档 | 已完成 | `docs/reports/archive/acceptance-v0.1.10-real-gitlab.md` | 文档引用复核 | v0.1.10 已被 v0.1.11 和场景矩阵覆盖，顶层 reports 目录瘦身 |
| 累积冲突清理脚本 | 已实现 | `scripts/e2e/reset-gitlab-seed-branches.sh` | dry-run 语法/帮助验证 | 默认 dry-run，`--execute` 才删除非种子分支，保留 main 与 seed feature 分支 |

---

## 3. 已验证事项

| 事项 | 验证方式 | 报告路径 | 结论 |
|---|---|---|---|
| 全链路核心闭环 | 真实 GitLab 验收 v0.1.10 | `docs/reports/archive/acceptance-v0.1.10-real-gitlab.md` | 20/20 PASS |
| v0.1.11 全链路 + 三层关联 + 多 Provider + 设置持久化 | 真实 GitLab 验收 v0.1.11 终轮 | `docs/reports/acceptance-v0.1.11-real-gitlab.md` | **25 PASS / 0 FAIL / 1 SKIP**（SKIP 为业务正确拒绝） |
| 场景化验收矩阵基线复验 | `bash scripts/acceptance/run-acceptance.sh` | `docs/reports/scenario-acceptance-matrix.md` | **134 PASS / 0 FAIL / 0 SKIP**；新增 SA-011 Git 访问异常真实 GitLab 强证据 |
| SA-010 解除挂载 UI E2E 复核 | `cd frontend && pnpm run test:e2e:slice-1` | `tasks/records/2026-05-19-sa-010-detach-ui-e2e.md` | **11 PASS / 0 FAIL / 0 SKIP**；新增详情页解除挂载用户旅程 |
| SA-011 Git 访问异常前端旅程 | `pnpm exec playwright test e2e/tests/slice-2-full-flow.spec.ts` | `tasks/records/2026-05-20-sa-011-git-access-evidence.md` | **23 PASS / 0 FAIL / 0 SKIP**；新增 `GIT_PERMISSION_DENIED` / `GIT_UNAVAILABLE` 类型分布、阻断级别、建议处理方式和外部 Git 访问处理入口 |
| SA-011 版本领先风险前端观察 | `pnpm exec playwright test e2e/tests/slice-2-full-flow.spec.ts` | `docs/reports/scenario-acceptance-matrix.md` | **22 PASS / 0 FAIL / 0 SKIP**；新增 `REPO_AHEAD` / `SYSTEM_AHEAD` 详情观察和同步请求语义断言 |
| SA-016 发布窗口报告导出 | `mvn -pl releasehub-bootstrap -am -Dtest=WindowRunApiTest -Dsurefire.failIfNoSpecifiedTests=false test` + `pnpm exec vitest run src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts` + `pnpm run typecheck` + `pnpm i18n:lint` | `tasks/records/2026-05-17-sa-016-release-report-export.md` | 后端 JSON/CSV 报告导出和前端详情页 CSV 导出入口通过 |
| URL 双重 encode 修复连带 release 分支创建 | 同上场景 4 | 同上 | 1/3 → **3/3** |
| Listener 异常隔离 | 同上后端日志 | 同上 | UnexpectedRollback 出现次数 2 → **0** |
| 前端 Playwright E2E 基线刷新 | `cd frontend && pnpm run test:e2e` | 本会话 2026-05-15 | **29 PASS / 0 FAIL / 0 SKIP**；登录、Slice-1、Slice-2 可跑通，历史显式 skip 已清零 |
| 本地统一环境入口 | `scripts/dev/start-local-env.sh hold/status/stop` | 本会话 2026-05-15 | 后端、前端和前端 `/api` 代理均可用；停止后 8080/5173 无残留监听 |
| Maven 插件版本显式化 | `mvn -pl releasehub-bootstrap -DskipTests validate` | 本会话 2026-05-13 | surefire/failsafe version missing 警告消失，targeted surefire 测试通过 |
| 单测基线 | `mvn test` | 本会话 2026-05-11 | 161 用例全过（含 GitLabGitBranchAdapterTest ENC 同步纠正） |
| 前端 Vitest / typecheck / i18n lint | `pnpm run test -- src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts src/views/release-window/__tests__/VersionUpdateDialog.spec.ts src/views/release-window/__tests__/OrchestrationPanel.spec.ts src/api/modules/__tests__/releaseWindow.spec.ts` / `pnpm run typecheck` / `pnpm i18n:lint` | 2026-05-15 | 25 Vitest 通过；vue-tsc 通过；`CalendarView.vue`、`RepositoryEdit.vue` 既有硬编码中文已清理，i18n lint 通过 |

---

## 4. 进行中事项

| 事项 | 当前状态 | 下一步 | 验收标准 |
|---|---|---|---|
| 场景矩阵驱动推进 | 2026-05-20 已补 SA-011 Git 访问异常真实 GitLab/Playwright 证据，`run-acceptance.sh` 5.9 可稳定制造权限不足和 Git 不可达探针仓库并检出 `GIT_PERMISSION_DENIED`/`GIT_UNAVAILABLE`，Slice-2 已补对应前端用户旅程；2026-05-19 已补 SA-010 窗口详情解除挂载入口和 Slice-1 UI E2E 复核；2026-05-18 已把 SA-012/SA-013/SA-014/SA-015/SA-016 P0 收口；最新脚本矩阵验收 134/0/0，Slice-2 前端回归 23/0/0；SA-015 已补分组筛选复核、窗口详情冲突证据复核、Run 详情部分失败复核和真实部分失败重试后端/GitLab 证据；SA-016 已补发布窗口报告导出 API 和详情页 CSV 导出入口；分组相关窄域文案已去特征化为“分组/叶子分组”；SA-010 发布计划和 SA-011 冲突严重级别/建议处理方式已补前端观察；SA-011 已补 `MERGE_CONFLICT`、`CROSS_REPO_VERSION_MISMATCH`、`REPO_AHEAD`、`SYSTEM_AHEAD`、`GIT_PERMISSION_DENIED`、`GIT_UNAVAILABLE` 类型后端/GitLab 强证据和前端详情观察，且版本类冲突解决候选已覆盖细分类型；SA-012 已补 feature 缺失发布计划观察、feature 缺失后端/GitLab 强证据、release 分支已存在后端/GitLab 强证据、分支名不合规外部处理前端观察和后端/GitLab 强证据 | 按矩阵当前推进队列继续补带仓库解除挂载真实 GitLab 分支归档复核与更完整发布计划限制 | 每个场景都同时具备前端用户旅程、后端业务约束、真实 GitLab/数据证据，并在矩阵中更新状态 |
| 前端用户旅程自动化验证 | 2026-05-20 SA-011 已补 Git 访问异常 Playwright 用户旅程，冲突面板展示 `GIT_PERMISSION_DENIED`/`GIT_UNAVAILABLE` 类型分布、阻断级别、建议处理方式和外部 Git 访问处理入口，且不会误触发版本同步；2026-05-19 SA-010 窗口详情已补解除挂载按钮、确认、调用 `detach` API 与刷新关联迭代列表的 Vitest 证据，并补 Slice-1 Playwright 旅程：UI 创建迭代、挂载到窗口、详情页解除挂载、断言关联列表为空且发布计划隐藏；2026-05-20 Slice-2 回归 23/0/0，已覆盖 SA-011 `REPO_AHEAD` / `SYSTEM_AHEAD` 和 Git 访问异常前端专项观察；2026-05-15 完整 Playwright 回归 30/0/0，CLOSED 窗口隐藏挂载入口已覆盖；SA-016 详情页已提供发布报告 CSV 导出入口；SA-015 已由 UI 真实生成失败 Run，并可按 `windowKey` + 分组 + `FAILED` 复核 Run 抽屉证据，也可在窗口详情复核 `MERGE_CONFLICT`、`BRANCH_NONCOMPLIANT`、`CROSS_REPO_VERSION_MISMATCH` 冲突类型分布、分支/版本详情和建议处理方式，并可在 Run 详情复核一个 Run 内成功项与失败项并存、失败任务重试次数和错误信息；SA-015/SA-016 已补真实 GitLab 部分失败重试后端证据，确认 retry 只选择失败项且不重复执行成功项；SA-012 冲突面板已展示版本冲突同步路径、分支名不合规和 release 分支已存在外部处理路径、阻断级别和建议处理方式，发布计划已展示 feature 分支缺失状态；SA-012 feature 缺失已有 GitLab 直查、`branch-status` 和 Orchestrate RunStep 强证据；SA-012 release 分支已存在已有 GitLab 预置/直查、`branch-status` 和 Attach RunStep 强证据；SA-012 分支名不合规已有 GitLab 分支直查、BranchRule check 和 `BRANCH_NONCOMPLIANT` 冲突扫描强证据；SA-011 `MERGE_CONFLICT` 已有 GitLab 分支直查、冲突提交、Attach Run `MERGE_BLOCKED` 和冲突扫描强证据，`CROSS_REPO_VERSION_MISMATCH` 已有两仓 targetVersion 差异、GitLab feature/release 分支直查和冲突扫描强证据，`REPO_AHEAD`/`SYSTEM_AHEAD` 已有真实 feature 分支版本差异和冲突扫描强证据；SA-010 发布计划面板已展示计划顺序、迭代、仓库和分支状态 | 继续补带仓库解除挂载真实 GitLab 分支归档复核与更完整发布计划限制 | Playwright 能从前端完成关键动作、观察结果，并与后端/GitLab 强证据形成闭环 |

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

> 当前主线按 `docs/reports/scenario-acceptance-matrix.md` 推进；优先级以场景矩阵缺口为准。

| 优先级 | 事项 | 原因 | 验收标准 |
|---|---|---|---|
| P1 | SA-010 发布计划与解除挂载收口 | attach 和冲突阻断已有强证据，发布计划、解除挂载入口、解除挂载 Slice-1 UI E2E、冲突严重级别、建议处理方式以及 SA-011 六类风险详情均已补前端观察；`MERGE_CONFLICT`/`CROSS_REPO_VERSION_MISMATCH`/`REPO_AHEAD`/`SYSTEM_AHEAD`/`GIT_PERMISSION_DENIED`/`GIT_UNAVAILABLE` 已补真实 GitLab 后端强证据 | 补带仓库解除挂载真实 GitLab 分支归档复核与更完整发布计划限制 |
| P1 | SA-015 复核扩展 | P0 已能由 UI 生成失败 Run 并复核失败步骤；分组筛选、窗口详情冲突证据复核、Run 详情部分失败复核、真实部分失败重试后端/GitLab 证据和发布报告导出已补 | 后续保持回归 |
| P1 | SA-016 收尾扩展 | P0 已覆盖，重复关闭幂等、真实部分失败重试和发布报告导出已补 | 后续转入 CI pipeline 触发等 P2 扩展 |
| P1/P2 | SA-012 更多冲突解决路径 | 版本冲突 `USE_SYSTEM`、feature 缺失、release 分支已存在和分支名不合规均已有对应证据 | 后续仅保留更多冲突类型解决路径扩展 |

---

## 7. 关键证据索引

| 证据 | 路径 | 说明 |
|---|---|---|
| 最末验收报告 | `docs/reports/scenario-acceptance-matrix.md` | 2026-05-20 SA-011 Git 访问异常真实 GitLab/Playwright 证据；真实 GitLab 验收基线：134 PASS / 0 FAIL / 0 SKIP；当前推进队列在第七节 |
| 前端 E2E 基线 | `frontend/e2e/tests` | 2026-05-19 Slice-1 回归：11 PASS / 0 FAIL / 0 SKIP；新增 SA-010 解除挂载 UI 旅程。2026-05-20 Slice-2 回归：23 PASS / 0 FAIL / 0 SKIP；新增 SA-011 Git 访问异常前端旅程；入口 `cd frontend && pnpm run test:e2e` |
| v0.1.11 真实 GitLab 报告 | `docs/reports/acceptance-v0.1.11-real-gitlab.md` | 25 PASS / 0 FAIL / 1 SKIP |
| 上轮验收报告 | `docs/reports/archive/acceptance-v0.1.10-real-gitlab.md` | 20/20 PASS，含 2 处已知限制 |
| 验收脚本 | `scripts/acceptance/run-acceptance.sh` | v3.12，含服务生命周期、`--hold-services`、SA-011 MERGE_CONFLICT、CROSS_REPO_VERSION_MISMATCH、REPO_AHEAD、SYSTEM_AHEAD、GIT_PERMISSION_DENIED、GIT_UNAVAILABLE 强证据、SA-015/SA-016 真实部分失败重试、SA-012 分支名不合规强证据、SA-013 干净黄金路径、SA-014 GitLab commit 校验 |
| 本地统一启停脚本 | `scripts/dev/start-local-env.sh` | `start|hold|stop|restart|status`；推荐用 `hold` 托管前后端联调环境 |
| 种子初始化 | `scripts/e2e/init-gitlab.sh` | 幂等，3 个种子仓库 |
| 种子分支清理 | `scripts/e2e/reset-gitlab-seed-branches.sh` | 默认 dry-run；`--execute` 清理非种子分支，保留 main 与 seed feature 分支 |
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
