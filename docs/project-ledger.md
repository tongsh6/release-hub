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
| 场景化验收矩阵基线 | 已验证 | `docs/reports/scenario-acceptance-matrix.md` + `scripts/acceptance/run-acceptance.sh` | 2026-05-15 真实 GitLab 验收 | PASS=50 / FAIL=0 / SKIP=0；SA-012/SA-013/SA-014/SA-016 P0 已覆盖 |
| 前端 SA-012/SA-013/SA-014 用户触发旅程 | 已验证 | `frontend/e2e/tests/slice-2-full-flow.spec.ts` | 2026-05-14 真实前后端联调 + 前端请求证据 | UI 创建业务数据后完成冲突解决、编排和版本更新触发；最终请求体断言作用域正确 |
| 前端 Playwright E2E 基线 | 已验证 | `frontend/e2e/tests` | 2026-05-15 真实前后端联调 | 29 PASS / 0 FAIL / 0 SKIP；历史显式 skip 已转为可执行旅程 |
| 本地环境统一启停脚本 | 已验证 | `scripts/dev/start-local-env.sh` | 2026-05-15 真实前后端联调 | `start|hold|stop|restart|status` 可用；`hold` 托管前后端；前端 `/api` 代理登录 200 |
| SA-016 发布后收尾闭环 | 已验证 | `scripts/acceptance/run-acceptance.sh` + `frontend/e2e/tests/slice-1-group-window.spec.ts` | 2026-05-15 真实 GitLab 验收 + Playwright | 关闭窗口、关闭后挂载/版本更新拒绝、收尾 Run 可见、前端 CLOSED 窗口隐藏挂载入口 |
| Maven surefire/failsafe 插件版本显式化 | 已验证 | `backend/pom.xml` | `mvn -pl releasehub-bootstrap -DskipTests validate` + `mvn -pl releasehub-application -Dtest=ConflictDetectionAppServiceTest test` | malformed POM 中插件版本缺失警告已关闭 |

---

## 3. 已验证事项

| 事项 | 验证方式 | 报告路径 | 结论 |
|---|---|---|---|
| 全链路核心闭环 | 真实 GitLab 验收 v0.1.10 | `docs/reports/acceptance-v0.1.10-real-gitlab.md` | 20/20 PASS |
| v0.1.11 全链路 + 三层关联 + 多 Provider + 设置持久化 | 真实 GitLab 验收 v0.1.11 终轮 | `docs/reports/acceptance-v0.1.11-real-gitlab.md` | **25 PASS / 0 FAIL / 1 SKIP**（SKIP 为业务正确拒绝） |
| 场景化验收矩阵基线复验 | `bash scripts/acceptance/run-acceptance.sh` | `docs/reports/scenario-acceptance-matrix.md` | **50 PASS / 0 FAIL / 0 SKIP** |
| URL 双重 encode 修复连带 release 分支创建 | 同上场景 4 | 同上 | 1/3 → **3/3** |
| Listener 异常隔离 | 同上后端日志 | 同上 | UnexpectedRollback 出现次数 2 → **0** |
| 前端 Playwright E2E 基线刷新 | `cd frontend && pnpm run test:e2e` | 本会话 2026-05-15 | **29 PASS / 0 FAIL / 0 SKIP**；登录、Slice-1、Slice-2 可跑通，历史显式 skip 已清零 |
| 本地统一环境入口 | `scripts/dev/start-local-env.sh hold/status/stop` | 本会话 2026-05-15 | 后端、前端和前端 `/api` 代理均可用；停止后 8080/5173 无残留监听 |
| Maven 插件版本显式化 | `mvn -pl releasehub-bootstrap -DskipTests validate` | 本会话 2026-05-13 | surefire/failsafe version missing 警告消失，targeted surefire 测试通过 |
| 单测基线 | `mvn test` | 本会话 2026-05-11 | 161 用例全过（含 GitLabGitBranchAdapterTest ENC 同步纠正） |
| 前端 Vitest / typecheck | `pnpm run test -- src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts src/views/release-window/__tests__/VersionUpdateDialog.spec.ts src/views/release-window/__tests__/OrchestrationPanel.spec.ts src/api/modules/__tests__/releaseWindow.spec.ts` / `pnpm run typecheck` | 2026-05-14 | 25 Vitest 通过；vue-tsc 通过 |

---

## 4. 进行中事项

| 事项 | 当前状态 | 下一步 | 验收标准 |
|---|---|---|---|
| 场景矩阵驱动推进 | 2026-05-15 已把 SA-012/SA-013/SA-014/SA-015/SA-016 P0 收口；最新脚本矩阵验收 51/0/0，SA-015 UI 旅程 4/0/0 | 按矩阵当前推进队列优先补 SA-012 更多冲突路径和 SA-010/SA-011 风险详情，随后补 SA-015 扩展复核 | 每个场景都同时具备前端用户旅程、后端业务约束、真实 GitLab/数据证据，并在矩阵中更新状态 |
| 前端用户旅程自动化验证 | 2026-05-15 完整 Playwright 回归 30/0/0，CLOSED 窗口隐藏挂载入口已覆盖；SA-015 已由 UI 真实生成失败 Run 并在 Run 抽屉复核 | 补冲突详情、更多冲突解决分支、发布计划可见性、部分失败/品牌筛选复核 | Playwright 能从前端完成关键动作、观察结果，并与后端/GitLab 强证据形成闭环 |

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
| P1 | SA-012 更多冲突解决路径 | 版本冲突 `USE_SYSTEM` 已闭环，但 release 分支已存在、feature 缺失、分支不合规等路径未覆盖 | 至少新增一个非版本冲突解决/阻断路径，并形成前端旅程 + 后端/GitLab 证据 |
| P1 | SA-010/SA-011 发布计划与风险详情 | attach 和冲突阻断已有强证据，前端发布计划可见性和风险详情仍弱 | 窗口详情可见发布计划、冲突严重级别和建议处理方式 |
| P1 | SA-015 复核扩展 | P0 已能由 UI 生成失败 Run 并复核失败步骤，冲突详情/部分失败/品牌筛选仍不足 | Playwright 从窗口详情/Run 详情观察冲突详情、部分失败和品牌筛选结果 |
| P1 | SA-016 收尾扩展 | P0 已覆盖，幂等关闭、部分失败重试和报告导出仍不足 | 补幂等关闭、部分失败重试和发布报告导出 |
| 可选 | 累积冲突清理脚本 | 验收幂等 + 累积真实分支冲突会影响 clean-room 复现 | 一键 reset 仓库到只剩 main + seed feature 分支 |
| 可选 | acc-v0.1.10 报告中段移到 archive | 已被 v0.1.11 报告完全覆盖 | reports/ 目录瘦身 |

---

## 7. 关键证据索引

| 证据 | 路径 | 说明 |
|---|---|---|
| 最末验收报告 | `docs/reports/scenario-acceptance-matrix.md` | 2026-05-15 SA-016 收口复验：50 PASS / 0 FAIL / 0 SKIP；当前推进队列在第七节 |
| 前端 E2E 基线 | `frontend/e2e/tests` | 2026-05-15 Playwright 真实前后端联调：29 PASS / 0 FAIL / 0 SKIP；入口 `cd frontend && pnpm run test:e2e` |
| v0.1.11 真实 GitLab 报告 | `docs/reports/acceptance-v0.1.11-real-gitlab.md` | 25 PASS / 0 FAIL / 1 SKIP |
| 上轮验收报告 | `docs/reports/acceptance-v0.1.10-real-gitlab.md` | 20/20 PASS，含 2 处已知限制 |
| 验收脚本 | `scripts/acceptance/run-acceptance.sh` | v3.6，含服务生命周期、`--hold-services`、SA-013 干净黄金路径、SA-014 GitLab commit 校验 |
| 本地统一启停脚本 | `scripts/dev/start-local-env.sh` | `start|hold|stop|restart|status`；推荐用 `hold` 托管前后端联调环境 |
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
