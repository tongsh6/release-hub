# Execution Roadmap / 执行路线图

> 本文件只回答一个问题：下一步做什么。
> 事实来源以 `docs/reports/scenario-acceptance-matrix.md` 和 `docs/project-ledger.md` 为准；本文件不得复制长篇证据、历史记录或实现细节。

---

## 1. 当前唯一主线

场景矩阵驱动收口。

权威来源：

- `docs/reports/scenario-acceptance-matrix.md`
- `docs/project-ledger.md`
- `tasks/records/`

执行规则：

- 每次用户要求“挑任务执行”时，只能选择第 2 节中标记为 `HEAD` 的队首任务。
- 不得从“后续保持回归”的事项中挑任务。
- 如果队首任务已完成，必须先更新本文件，再继续挑下一个任务。

---

## 2. 当前执行队列

| 顺序 | 标记 | SA | 任务 | 来源 | 选择理由 |
|---|---|---|---|---|---|
| 1 | HEAD | SA-015 | 更完整的前端场景复跑与证据更新 | `scenario-acceptance-matrix.md` 当前推进队列 | 数据源口径已统一，下一步用前端场景复跑确认页面旅程证据仍覆盖关键发布复核路径 |

---

## 3. 当前队首任务

任务：SA-015 更完整的前端场景复跑与证据更新。

验收出口：

- 明确本轮前端场景复跑范围，优先覆盖 Run/窗口详情复核、数据质量复核队列和发布候选评审入口，不盲目扩大到与当前缺口无关的页面。
- 使用现有 Playwright/Vitest/E2E 入口复跑关键页面旅程，并记录哪些证据属于真实页面旅程、哪些只是 route-level stub 或组件回归。
- 保持场景化用户旅程原则：ReleaseHub 业务数据不得用 API 或数据库脚本偷造后再声称完成完整用户旅程；API、数据库和 GitLab 查询只能作为复核证据。
- 如发现场景证据失效，先修复用户可观察路径或测试夹具，再更新验收矩阵，不把失败用例改成 skip。
- 复跑结果必须能说明 SA-015 发布状态和执行证据复核仍成立，并兼顾最近新增的 SA-002 数据质量复核页面边界展示。
- 同步更新 `scenario-acceptance-matrix.md`、`docs/project-ledger.md`、`docs/execution-roadmap.md`、`docs/openspec/specs/` 和 `tasks/records/`。
- 完成后运行相关前端 E2E/组件测试，并至少运行 `bash scripts/dev/check-roadmap.sh`、`pnpm run typecheck`、`pnpm i18n:lint` 和 `git diff --check`。

当前输入基线：

- 发布候选报告：`docs/reports/release-candidate-2026-05-23.md`。
- 发布候选评审页：`frontend/src/views/release-governance/ReleaseCandidateReview.vue`。
- 全量验收基线：170 PASS / 0 FAIL / 0 SKIP。
- 数据质量风险：188 条待复核动作，已具备应用内复核队列。
- 最新静态扫描：`.ai/reports/static-scan/20260523-202310/summary.md`。
- 命名空间元数据：`dataNamespace`、`reviewBatchId`、`assetScope`、`retentionPolicy` 已落到 safe-cleanup 动作、复核 API 和复核队列页面。
- 数据源口径：`API_VISIBLE_ASSETS`、`DB_AUDIT_ASSETS`、`REVIEW_QUEUE_ACTIONS` 已落到全量验收输出、safe-cleanup 报告、复核 API 和复核队列页面。

已完成的前置事项：

- SA-001 发布候选收口报告已形成：`docs/reports/release-candidate-2026-05-23.md`。
- SA-001 发布候选评审页 / 发布经理检查清单已页面化：`frontend/src/views/release-governance/ReleaseCandidateReview.vue`。
- release-governance OpenSpec 已新增：`docs/openspec/specs/release-governance/spec.md`。
- SA-002 数据质量复核队列已页面化：`frontend/src/views/data-quality/DataQualityReviewQueue.vue`。
- SA-002 验收数据命名空间与保留策略已落地：`tasks/records/2026-05-23-sa-002-acceptance-data-namespace-retention.md`。
- SA-002 验收脚本与应用 API 数据源口径已统一：`tasks/records/2026-05-23-sa-002-data-source-boundary-alignment.md`。
- 同步更新 `scenario-acceptance-matrix.md`、`docs/project-ledger.md`、`docs/execution-roadmap.md` 和 `tasks/records/`。

非目标：

- 不做 RBAC。
- 不做通知。
- 不做批量组织重构或资源迁移向导。
- 不做仓库自动拆分、跨分组批量迁移或自动容量规划。
- 不自动关闭 DRAFT 发布窗口。
- 不直接修改数据库，不删除或迁移业务数据。
- 不触碰 GitLab 远端资源。
- 不引入 RBAC、通知或新的审批工作流引擎。
- 不改变分支创建、发布编排、关闭窗口、CI 触发状态、retry、Maven/Gradle 或已有版本更新写回语义。
- 不引入新的全局工具安装或系统级依赖；若需要新依赖，必须先按本机策略确认。
- 不通过数据库脚本绕过应用层不变量来执行清理动作。
- 不把 PDF 导出、RBAC、通知或批量组织迁移重新塞入当前阶段，除非清账结果明确将其列为下一阶段候选并写清验收标准。

---

## 4. 暂缓项

| 事项 | 原因 |
|---|---|
| RBAC | `docs/project-ledger.md` 明确当前阶段不做 |
| 通知 | `docs/project-ledger.md` 明确当前阶段不做 |
| CI 深集成 | 当前阶段不做；SA-016 发布证据归档形态已按制品包收口 |
| 批量组织重构/资源迁移向导 | SA-003 已按受控空叶子分组移动收口；批量资源迁移、跨 Git provider 迁移和重写发布范围不进入当前阶段 |
| 自动批量删除存量数据 | SA-002 当前只允许以 dry-run、人工复核和最小可审计动作推进 |

---

## 5. 防腐败规则

- 本文件只保留任务队列指针，不沉淀验收证据。
- `HEAD` 必须且只能有一个。
- `HEAD` 行必须包含一个 `SA-xxx` 编号，且该编号必须存在于 `scenario-acceptance-matrix.md`。
- `HEAD` 行不得包含“或”“任选”“待定”“二选一”等不确定词。
- `HEAD` 行不得是“后续保持回归”。
- 队首任务完成后必须出队；不能把已完成任务长期留在 `HEAD`。
- 每次修改本文件后运行：

```bash
bash scripts/dev/check-roadmap.sh
```
