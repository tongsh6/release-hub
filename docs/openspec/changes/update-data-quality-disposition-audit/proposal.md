# Change: 数据质量受控处置执行审计

## Why

SA-002 已能识别、复核并展示存量数据风险的处置策略，但还缺少受控执行审计模型。没有该模型时，发布经理只能看到“应该去哪里处理”，却无法在产品内追踪谁基于哪条 dry-run 动作创建了处置记录、执行前状态是什么、处理后如何复核、失败如何恢复以及重复提交如何防重。

需求文档：`docs/requirements/in-progress/SA-002-数据质量受控处置执行审计.md`

## What Changes

- 新增数据质量处置 case 概念，承接已接受的 dry-run 复核动作。
- 新增只做审计编排的状态流转：`PLANNED`、`IN_PROGRESS`、`VERIFICATION_PENDING`、`VERIFIED`、`FAILED`、`CANCELLED`。
- 为每个 case 固化动作来源、动作快照、执行前快照、执行后复核、失败恢复、幂等键和审计记录。
- 应用层人工处置只跳转到既有应用入口，不由复核队列直接修改业务数据。
- `OBSERVE_ONLY`、`MIGRATION_REQUIRED` 和暂缓风险只允许创建可见审计记录或阻断记录，不允许从复核队列直接执行。

## Complete Target Blueprint

### Final Behavior

发布经理或运维人员在数据质量复核队列中批准一条动作后，可以创建处置 case。case 记录来源报告、复核批次、资源、风险、处置等级、应用入口、执行前检查、允许动作、回滚边界和审计记录口径。操作者从 case 跳转到既有应用页面完成人工动作，再回到 case 记录执行后复核结果。系统对重复创建、重复开始和重复复核提供幂等防护。

### Full Scope

- Domain：新增处置 case 状态、处置等级、幂等键和审计快照值对象。
- Application：新增处置 case 创建、列表、开始、记录复核结果、记录失败和取消用例。
- Infrastructure：新增持久化表保存 case、动作快照、前后快照、失败原因和审计记录。
- API / DTO：新增 `/api/v1/data-quality/disposition-cases` 系列接口；复用 cleanup-review 返回的策略字段。
- Frontend：数据质量复核队列增加“创建处置 case / 查看 case”入口，新增 case 列表和详情抽屉。
- Database / Migration：新增处置 case 表；不修改业务表、不迁移存量业务数据。
- Permission / Audit：当前不引入 RBAC；先记录操作者、时间、来源报告、动作快照和复核证据。
- Tests：应用层、MockMvc、前端组件、typecheck、i18n、roadmap、静态扫描。
- Docs：OpenSpec、场景矩阵、项目台账、路线图、任务记录。

### Non-Goals

- 不在复核队列中直接删除数据库记录。
- 不自动关闭 DRAFT 发布窗口。
- 不自动迁移 `iteration_repo.branch_creation_mode`。
- 不触碰 GitLab 远端分支或仓库。
- 不引入 RBAC、通知或新的审批工作流引擎。
- 不把处置 case 伪装成业务动作执行器；业务动作仍由既有应用页面和服务完成。

### Final Architecture Shape

- `DataQualityDispositionCase`：处置执行审计 case 聚合，保存来源动作快照和状态。
- `DataQualityDispositionCaseAppService`：负责创建 case、幂等判断、状态推进和复核记录。
- `DataQualityDispositionCasePort`：持久化端口。
- `DataQualityDispositionCaseJpaAdapter`：JPA 持久化实现。
- `DataQualityDispositionController`：REST API。
- `DataQualityReviewQueue.vue`：从复核结果创建或跳转 case。
- `DataQualityDispositionCases.vue`：case 列表与详情复核入口。

### Phased Plan

| Slice | Blueprint Area | Goal | Dependency | This Change? | Tracking Location |
|---|---|---|---|---|---|
| 1 | 设计门禁 | 形成需求、OpenSpec proposal、设计、delta spec 和任务记录 | SA-002 处置策略 | Yes | 本 change |
| 2 | 最小审计模型 | 创建/list case，记录 action 快照、幂等键和状态，不执行清理 | Slice 1 | No | `docs/execution-roadmap.md` 下一 HEAD |
| 3 | 页面化 | 复核队列创建 case，case 列表/详情可见，支持记录复核结果 | Slice 2 | No | 后续任务记录 |
| 4 | 受控处置扩展 | 对个别应用层人工处置补执行前快照与执行后复核模板 | Slice 3 | No | 后续 OpenSpec/tasks |
| 5 | 独立迁移服务 | 若需要处理 BranchCreationMode 风险，另建迁移服务 proposal | Slice 1 | No | 单独 change |

### Acceptance Matrix

| Acceptance | Verification | Slice | Status |
|---|---|---|---|
| 需求文档与 OpenSpec proposal 双向引用 | `rg` 检查路径互链 | 1 | Done |
| 设计覆盖动作来源、前后快照、失败恢复、幂等和验收证据 | 人工复核 `design.md` | 1 | Done |
| delta spec 定义处置 case 行为和禁止边界 | 人工复核 `specs/data-quality/spec.md` | 1 | Done |
| OpenSpec CLI 严格校验 | `openspec validate update-data-quality-disposition-audit --strict` | 1 | Not run: 本机无 `openspec` 命令 |
| 最小 case API 不修改业务数据 | 应用层/API 测试 | 2 | Not started |
| 前端可创建/查看 case 且无执行清理按钮 | Vitest + i18n/typecheck | 3 | Not started |

### Risks / Rollback

- 风险：case 被误解为自动清理执行器。缓解：命名、文案、API 和状态均强调审计与人工处置；无直接执行清理端点。回滚：删除入口，保留 dry-run 与复核队列。
- 风险：幂等键设计不足导致重复 case。缓解：以 `reviewBatchId + sourceReport + resourceType + resourceId + riskType + actionHash` 形成唯一键。回滚：合并重复 case，保留最早创建记录。
- 风险：执行前快照记录敏感信息。缓解：快照按白名单字段脱敏保存，不记录 token 明文。回滚：清空敏感快照字段并补审计说明。
- 风险：BranchCreationMode 风险被过早执行。缓解：`MIGRATION_REQUIRED` 只生成阻断 case，不提供执行入口。回滚：取消 case 并要求独立迁移服务 proposal。

## Impact

- Affected specs: `data-quality`
- Affected docs: `docs/requirements/INDEX.md`、`docs/reports/scenario-acceptance-matrix.md`、`docs/project-ledger.md`、`docs/execution-roadmap.md`、`tasks/records/`
- Affected code for future slices: data-quality application service、controller、frontend review queue、new disposition case views
