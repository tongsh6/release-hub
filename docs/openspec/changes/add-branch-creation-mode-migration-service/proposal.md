# Change: BranchCreationMode 独立迁移服务

## Why

SA-002 已把 `BRANCH_CREATION_MODE_MISSING_OR_INVALID` 识别为 `MIGRATION_REQUIRED`，但当前只有风险识别和阻断 case，没有受控迁移服务设计。若直接从复核队列或脚本写入 `iteration_repo.branch_creation_mode`，会绕过迭代领域语义、缺少回滚证据，也无法区分“可安全补齐 AUTO”和“需要人工判定 NAMED/EXISTING”的历史数据。

需求文档：`docs/requirements/in-progress/SA-002-BranchCreationMode独立迁移服务设计.md`

## What Changes

- 新增 BranchCreationMode 独立迁移服务的设计门禁，承接 SA-002 `MIGRATION_REQUIRED` 风险。
- 定义迁移范围：仅历史 `iteration_repo.branch_creation_mode` 缺失、空白、大小写/空格可规范化或非法值；不修改其他业务字段。
- 定义 dry-run 候选分类、人工确认、受控执行、幂等、防重、迁移前后快照、失败回滚和审计记录。
- 定义验收证据：dry-run 统计、执行计划、执行后 API/version-info 复核、数据库只读审计、回滚演练或失败记录。
- 保留实现前审批门禁：本 change 当前只完成 proposal，不启动执行器或数据库写入实现。

## Review Decision

2026-05-24 评审结论：`APPROVE_DRY_RUN_ONLY`。

评审记录：`docs/openspec/changes/add-branch-creation-mode-migration-service/review.md`。

只批准下一切片实现最小 dry-run：只读扫描、候选分类、Markdown/JSON 报告和应用层测试。不批准执行 API、数据库写入、迁移审计表、前端执行入口或 GitLab 远端操作。

## Complete Target Blueprint

### Final Behavior

发布经理或运维人员从 SA-002 数据质量报告看到 BranchCreationMode 风险后，不能直接在复核队列执行迁移。系统提供独立迁移服务入口，先生成只读 dry-run 批次，列出每条历史 `iteration_repo` 记录的当前值、候选迁移动作、推断依据、风险等级和是否需要人工映射。人工批准后，服务只写入 `iteration_repo.branch_creation_mode`，并记录迁移前快照、迁移后复核、批次审计、幂等键和回滚清单。

### Full Scope

- Domain：复用 `BranchCreationMode` 枚举语义；新增迁移候选分类和迁移批次状态。
- Application：新增 BranchCreationMode migration service，支持 dry-run、生成执行计划、受控执行、复核、失败记录和回滚计划导出。
- Infrastructure：只读扫描 `iteration_repo`；受控执行时仅更新 `branch_creation_mode`；保存迁移批次和候选审计记录。
- API / DTO：新增只读 dry-run 与批次复核 API；执行 API 需要显式批次确认和操作者。
- Frontend：后续可在数据质量处置 case 中展示迁移 proposal 入口和批次状态；本 change 不要求页面实现。
- Database / Migration：未来实现可新增迁移审计表；不得修改 `feature_branch`、版本字段、发布窗口、仓库集合或 GitLab 远端。
- Permission / Audit：当前不引入 RBAC；必须记录操作者、来源报告、批次 ID、候选快照、执行结果和回滚材料。
- Tests：dry-run 分类、幂等执行、非法候选拒绝、执行后 version-info/API 复核、回滚演练、typecheck/i18n/roadmap/static scan。
- Docs：OpenSpec、需求文档、场景矩阵、项目台账、路线图、任务记录。

### Non-Goals

- 不从数据质量复核队列直接写库。
- 不自动关闭发布窗口、不删除数据、不修改迭代仓库集合。
- 不修改 `feature_branch`、`base_version`、`dev_version`、`target_version` 或 `version_source`。
- 不触碰 GitLab 远端分支、仓库、tag、MR 或 CI。
- 不把无法判断的历史记录强行写成 `NAMED` 或 `EXISTING`。
- 不引入 RBAC、通知或审批流引擎。
- 不在本设计门禁切片实现执行器或数据库迁移。

### Final Architecture Shape

- `BranchCreationModeMigrationBatch`：一次迁移 dry-run / 执行批次，记录来源报告、操作者、状态、统计和审计路径。
- `BranchCreationModeMigrationCandidate`：单条候选，记录 `iterationKey`、`repoId`、原始 mode、feature 分支、建议 mode、推断依据、风险等级和人工确认状态。
- `BranchCreationModeMigrationService`：应用服务，负责 dry-run、执行计划、受控执行、复核与回滚计划导出。
- `BranchCreationModeMigrationPort`：只读扫描与受控写入端口；写入方法只能更新 `branch_creation_mode`。
- `DataQualityDispositionCase`：继续作为 SA-002 风险入口；`MIGRATION_REQUIRED` case 只能指向迁移服务 proposal 或批次，不执行迁移。

### Candidate Classification

| 分类 | 条件 | 默认动作 | 是否可自动进入执行计划 |
|---|---|---|---|
| `SAFE_AUTO_DEFAULTABLE` | mode 缺失/空白，且 `feature_branch = feature/{iterationKey}` | 建议写入 `AUTO` | 可以，但仍需批次人工批准 |
| `NORMALIZE_LEGAL_VALUE` | mode 去空格/转大写后为 `AUTO`、`NAMED` 或 `EXISTING` | 建议写入规范化枚举值 | 可以，但仍需批次人工批准 |
| `MANUAL_MAPPING_REQUIRED` | feature 分支为 `feature/*` 但无法仅凭本地数据区分 `NAMED` 与 `EXISTING`，或 mode 为未知业务值 | 生成人工映射项 | 不可以，必须上传/填写人工确认结果 |
| `NOT_MIGRATABLE_IN_THIS_SERVICE` | 缺少 feature 分支、分支不在 `feature/` 路径、关联记录不完整或需要修改其他业务字段 | 拒绝迁移并输出原因 | 不可以 |

### Phased Plan

| Slice | Blueprint Area | Goal | Dependency | This Change? | Tracking Location |
|---|---|---|---|---|---|
| 1 | 设计门禁 | 形成需求、OpenSpec proposal、设计、delta spec、任务记录和路线图同步 | SA-002 处置 case | Yes | 本 change |
| 2 | 最小 dry-run | 只读扫描历史 `iteration_repo`，输出候选分类、统计和 JSON/Markdown 报告 | Slice 1 approval | No | 后续 task |
| 3 | 执行计划 | 从 dry-run 批次生成需人工批准的执行计划，拒绝未确认候选 | Slice 2 | No | 后续 task |
| 4 | 受控执行 | 仅更新 `branch_creation_mode`，记录批次审计、幂等键和前后快照 | Slice 3 | No | 后续 task |
| 5 | 复核与回滚 | API/version-info 复核、数据库只读审计、回滚演练或失败记录 | Slice 4 | No | 后续 task |

### Acceptance Matrix

| Acceptance | Verification | Slice | Status |
|---|---|---|---|
| 需求文档与 OpenSpec proposal 双向引用 | `rg` 检查路径互链 | 1 | Done |
| 设计覆盖迁移范围、候选分类、审计、回滚和验收证据 | 人工复核 `design.md` | 1 | Done |
| delta spec 定义 `MIGRATION_REQUIRED` 进入独立迁移服务，且复核队列不能执行迁移 | 人工复核 `specs/data-quality/spec.md` | 1 | Done |
| iteration delta spec 明确只允许受控服务补齐 `branch_creation_mode` | 人工复核 `specs/iteration/spec.md` | 1 | Done |
| proposal 评审明确下一步是否进入 dry-run | `review.md` + 任务记录 | 1 | Done: APPROVE_DRY_RUN_ONLY |
| OpenSpec CLI 严格校验 | `openspec validate add-branch-creation-mode-migration-service --strict` | 1 | Not run: 本机无 `openspec` 命令 |
| dry-run 报告生成候选分类与统计 | 后续专项测试 | 2 | Not started |
| 受控执行只更新 `branch_creation_mode` 且可幂等重跑 | 后续应用层/集成测试 | 4 | Not started |
| 执行后 version-info/API 与 DB 只读审计一致 | 后续验收脚本 | 5 | Not started |

### Risks / Rollback

- 风险：把无法推断的历史记录误写成 `NAMED` 或 `EXISTING`。缓解：默认进入 `MANUAL_MAPPING_REQUIRED`，无人工映射不得执行。回滚：使用批次前快照恢复原始 `branch_creation_mode`。
- 风险：迁移服务被误用为数据质量复核队列的直接执行按钮。缓解：复核队列只展示 proposal/批次入口；执行必须在独立服务中带批次确认。回滚：禁用执行端点，保留 dry-run。
- 风险：执行中断导致部分记录已写入。缓解：候选级幂等键和批次状态；重复执行跳过已完成且前后值一致的候选。回滚：按批次回滚清单逐条恢复。
- 风险：非法 `branch_creation_mode` 当前会导致读取 version-info 时枚举解析失败。缓解：迁移前 dry-run 必须输出会影响 API 读取的非法值；实现切片另行决定是否补只读容错。回滚：保持当前代码不变，只推进已批准迁移。

## Impact

- Affected specs: `data-quality`、`iteration`
- Affected docs: `docs/requirements/INDEX.md`、`docs/reports/scenario-acceptance-matrix.md`、`docs/project-ledger.md`、`docs/execution-roadmap.md`、`tasks/records/`
- Affected code for future slices: iteration application service or dedicated migration app service, persistence adapter, migration audit table, data-quality disposition case entry point
