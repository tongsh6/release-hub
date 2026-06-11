# Design: SA-002 BranchCreationMode 独立迁移服务

## 设计原则

1. 迁移服务只处理 `iteration_repo.branch_creation_mode`，不修补其他业务字段。
2. dry-run 是默认入口；没有人工批准的迁移批次不得写库。
3. 无法从本地数据确定真实语义时，不做猜测，转为人工映射。
4. 复核队列和处置 case 只提供入口与审计，不直接执行迁移。
5. 每次执行都必须能用批次前快照解释、复核和回滚。

## 迁移对象

纳入范围：

- `iteration_repo.branch_creation_mode IS NULL`。
- `branch_creation_mode` 为空白字符串。
- `branch_creation_mode` 去空格/转大写后可规范化为 `AUTO`、`NAMED`、`EXISTING`。
- `branch_creation_mode` 为非法值，但操作者能通过人工映射确认目标枚举。

排除范围：

- 修改 `feature_branch`。
- 修改版本字段：`base_version`、`dev_version`、`target_version`、`version_source`、`version_synced_at`。
- 修改发布窗口、迭代、仓库集合、Run、冲突记录或数据质量 case。
- 调用 GitLab API、创建/删除/重命名远端分支。
- 通过 SQL 脚本绕过迁移服务批次审计。

## 核心模型

### BranchCreationModeMigrationBatch

| 字段 | 含义 |
|---|---|
| `batchId` | 批次 ID |
| `sourceReport` | 来源 SA-002 dry-run 或处置 case |
| `requestedBy` | 创建人 |
| `status` | `DRY_RUN_CREATED` / `PLAN_APPROVED` / `EXECUTING` / `EXECUTED` / `VERIFIED` / `FAILED` / `ROLLED_BACK` |
| `candidateCount` | 候选总数 |
| `autoDefaultableCount` | 可安全补齐 AUTO 数 |
| `normalizeCount` | 可规范化合法值数 |
| `manualMappingCount` | 需要人工映射数 |
| `rejectedCount` | 不在本服务处理数 |
| `preSnapshotPath` | 迁移前快照路径 |
| `postSnapshotPath` | 迁移后复核路径 |
| `rollbackPlanPath` | 回滚清单路径 |
| `auditNote` | 审计说明 |

### BranchCreationModeMigrationCandidate

| 字段 | 含义 |
|---|---|
| `candidateKey` | 幂等键 |
| `iterationKey` / `repoId` | 关联记录主键 |
| `currentModeRaw` | 原始 `branch_creation_mode` |
| `featureBranch` | 当前 feature 分支，只读 |
| `proposedMode` | 建议写入值 |
| `classification` | 候选分类 |
| `inferenceReason` | 推断依据 |
| `requiresManualMapping` | 是否需要人工映射 |
| `manualApprovedMode` | 人工确认目标值 |
| `preValue` / `postValue` | 执行前后值 |
| `status` | `PLANNED` / `SKIPPED` / `UPDATED` / `REJECTED` / `FAILED` / `ROLLED_BACK` |

## 候选分类规则

1. `SAFE_AUTO_DEFAULTABLE`
   - 条件：`branch_creation_mode` 缺失或空白，且 `feature_branch` 等于 `feature/{iterationKey}`。
   - 目标：`AUTO`。
   - 理由：历史三层关联实现前的默认行为就是自动创建 `feature/{iterationKey}`。

2. `NORMALIZE_LEGAL_VALUE`
   - 条件：原始值去空格/转大写后属于 `AUTO`、`NAMED`、`EXISTING`。
   - 目标：规范化后的枚举名。
   - 理由：只修正存储格式，不改变业务语义。

3. `MANUAL_MAPPING_REQUIRED`
   - 条件：feature 分支存在且在 `feature/` 路径下，但无法仅凭本地数据区分 `NAMED` 与 `EXISTING`；或非法值可能来自历史人工输入。
   - 目标：无默认写入值，必须人工确认。
   - 理由：不调用 GitLab，不能凭空判断分支是系统创建还是选择已有。

4. `NOT_MIGRATABLE_IN_THIS_SERVICE`
   - 条件：缺少 feature 分支、feature 分支不在 `feature/` 路径、关联主键异常、需要修改其他业务字段。
   - 目标：拒绝迁移，回到数据质量复核或独立业务处理。

## 幂等与防重

`candidateKey`：

```text
sha256(batchId + iterationKey + repoId + currentModeRaw + proposedMode + classification)
```

执行规则：

- 同一批次内同一 `iterationKey + repoId` 只能有一个可执行候选。
- 记录当前值已等于目标值时，标记 `SKIPPED`，不得重复写入。
- 执行前若数据库当前值与 dry-run 快照不一致，候选标记 `FAILED`，要求重新 dry-run。
- 批次级重跑只能处理 `PLANNED` 或可幂等确认的 `SKIPPED` 候选，不覆盖失败记录。

## 执行与回滚

执行前：

- 生成批次前快照，至少包含 `iterationKey`、`repoId`、`branch_creation_mode`、`feature_branch`、候选分类和目标值。
- 生成执行计划，列出会写入的记录和拒绝执行的记录。
- 人工确认执行计划；未确认时禁止执行。

执行中：

- 每条候选只允许执行：

```sql
UPDATE iteration_repo
SET branch_creation_mode = :targetMode
WHERE iteration_key = :iterationKey
  AND repo_id = :repoId
  AND branch_creation_mode IS NOT DISTINCT FROM :preValue;
```

- 不允许更新任何其他列。
- 受影响行数不是 1 时，该候选失败并保留原因。

执行后：

- 通过应用 API 或 version-info 读取 `branchCreationMode`，确认值可被领域枚举解析。
- 通过数据库只读审计确认批次记录全部落在允许目标值内。
- 输出批次后快照和回滚清单。

回滚：

- 回滚只恢复本批次写入前的 `branch_creation_mode` 原值。
- 回滚前仍需确认当前值等于本批次写入值，避免覆盖后续合法变更。
- 回滚失败必须逐条记录，不声明全局自动恢复。

## API 形态（后续实现候选）

```http
POST /api/v1/data-quality/branch-creation-mode-migrations/dry-run
GET  /api/v1/data-quality/branch-creation-mode-migrations/{batchId}
POST /api/v1/data-quality/branch-creation-mode-migrations/{batchId}/approve-plan
POST /api/v1/data-quality/branch-creation-mode-migrations/{batchId}/execute
POST /api/v1/data-quality/branch-creation-mode-migrations/{batchId}/verify
POST /api/v1/data-quality/branch-creation-mode-migrations/{batchId}/rollback-plan
```

本设计门禁不实现这些 API。后续最小实现应优先交付 dry-run 和报告，再决定是否进入执行切片。

## 与 SA-002 处置 case 的关系

- `MIGRATION_REQUIRED` case 可以展示本 proposal、迁移批次 ID 或 dry-run 报告路径。
- case 不展示“开始人工处置”或“执行迁移”按钮。
- case 可以记录迁移服务外部结果，例如批次已验证或失败，但不能代替迁移服务执行。

## 验收证据

设计门禁验收：

- 需求文档、OpenSpec proposal、design、tasks 和 delta spec 均存在并互链。
- 场景矩阵、项目台账、路线图和任务记录同步。
- `bash scripts/dev/check-roadmap.sh`、`git diff --check`、typecheck、i18n lint 和静态扫描通过。

后续实现验收：

- dry-run 报告包含候选分类、统计、执行计划和拒绝原因。
- 应用层测试覆盖分类、幂等、快照不一致拒绝、人工映射缺失拒绝。
- 执行测试证明只更新 `branch_creation_mode`。
- version-info/API 复核证明迁移后枚举可解析。
- 回滚演练或失败记录证明可恢复、可审计。

## 非目标

- 不实现执行器。
- 不改业务数据。
- 不触碰 GitLab。
- 不修复 feature 分支缺失或非法。
- 不扩展 RBAC、通知或审批流。
