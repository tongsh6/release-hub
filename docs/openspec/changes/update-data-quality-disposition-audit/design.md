# Design: SA-002 数据质量受控处置执行审计

## 设计原则

1. 处置 case 是审计对象，不是自动清理执行器。
2. 所有业务写入必须继续通过既有应用页面和应用服务完成。
3. 复核队列只负责把 dry-run 动作转成可追踪 case，并记录执行前后证据。
4. 幂等、防重和审计快照优先于“快速修复”。
5. 不记录 token 明文，不绕过业务不变量，不触碰 GitLab 远端资源。

## 核心概念

### DispositionCase

处置 case 表示“一条 dry-run 复核动作进入受控人工处置跟踪”。

核心字段：

| 字段 | 含义 |
|---|---|
| `id` | case ID |
| `caseKey` | 幂等键，基于来源报告、复核批次、资源、风险和动作 hash 生成 |
| `sourceReport` | dry-run 报告来源 |
| `reviewBatchId` | 复核批次 |
| `dataNamespace` | 数据命名空间 |
| `assetScope` | 资产范围 |
| `resourceType` / `resourceId` | 风险资源 |
| `riskType` | 风险类型 |
| `dispositionLevel` | `OBSERVE_ONLY` / `APPLICATION_MANUAL` / `MIGRATION_REQUIRED` / `DEFERRED` |
| `applicationEntry` | 目标应用入口或迁移服务入口 |
| `allowedAction` | 允许的人工动作 |
| `preExecutionCheck` | 执行前检查口径 |
| `postExecutionVerification` | 执行后复核口径 |
| `rollbackBoundary` | 失败恢复边界 |
| `auditRecord` | 审计记录口径 |
| `actionSnapshot` | dry-run 动作脱敏快照 |
| `preStateSnapshot` | 执行前业务状态脱敏快照 |
| `postStateSnapshot` | 执行后复核证据脱敏快照 |
| `status` | case 状态 |
| `requestedBy` / `handledBy` / `verifiedBy` | 操作者 |
| `failureReason` / `rollbackNote` | 失败与恢复说明 |

### 状态机

```text
PLANNED
  ├── start -> IN_PROGRESS
  ├── cancel -> CANCELLED
  └── block -> FAILED

IN_PROGRESS
  ├── mark-verification-pending -> VERIFICATION_PENDING
  ├── fail -> FAILED
  └── cancel -> CANCELLED

VERIFICATION_PENDING
  ├── verify -> VERIFIED
  ├── fail -> FAILED
  └── reopen -> IN_PROGRESS
```

状态语义：

| 状态 | 语义 |
|---|---|
| `PLANNED` | 已从复核动作创建 case，尚未开始人工处置 |
| `IN_PROGRESS` | 操作者已确认进入既有应用入口处理 |
| `VERIFICATION_PENDING` | 操作者声明已完成应用层动作，等待记录复核证据 |
| `VERIFIED` | 执行后复核证据已记录 |
| `FAILED` | 处置失败或被策略阻断，必须记录失败原因 |
| `CANCELLED` | 人工取消，不代表风险已消失 |

## 幂等与重复提交

`caseKey` 由以下字段生成：

```text
sha256(sourceReport + reviewBatchId + resourceType + resourceId + riskType + normalizedActionSnapshot)
```

规则：

- 同一 `caseKey` 只能有一个 active case。
- 重复创建返回既有 case，不新建记录。
- `VERIFIED` 后再次提交同一动作，应返回既有完成记录和复核证据。
- `FAILED` 后需要重试时，必须显式创建 retry case，并在 `retryOfCaseId` 中关联原 case。

## 快照策略

执行前快照：

- 只保存白名单字段，例如窗口状态、仓库 ID、cloneUrl hash、迭代 key、branchCreationMode 值。
- token、GitLab 原始响应、密钥、完整错误堆栈不得进入快照。
- 快照采集失败时，case 不得进入 `IN_PROGRESS`，必须保持 `PLANNED` 或 `FAILED` 并记录原因。

执行后复核：

- 保存人工复核结论、复核查询摘要和证据路径。
- API/数据库/GitLab 查询只能作为复核证据，不能替代前端用户旅程或既有应用动作。

## 不同处置等级的处理

| 处置等级 | case 行为 | 执行入口 |
|---|---|---|
| `OBSERVE_ONLY` | 可创建观察 case，记录复核结论；不允许进入 `IN_PROGRESS` | 数据质量 case 详情 |
| `APPLICATION_MANUAL` | 可创建 case 并跳转到既有应用入口；业务动作由原页面完成 | 仓库、设置、迭代、发布窗口 |
| `MIGRATION_REQUIRED` | 可创建阻断 case，记录迁移服务需求；不允许应用内执行 | 独立迁移服务 proposal |
| `DEFERRED` | 可记录暂缓原因和负责人；不允许执行 | 数据质量 case 详情 |

## API 形态

```http
POST /api/v1/data-quality/disposition-cases
GET  /api/v1/data-quality/disposition-cases
GET  /api/v1/data-quality/disposition-cases/{id}
POST /api/v1/data-quality/disposition-cases/{id}/start
POST /api/v1/data-quality/disposition-cases/{id}/mark-verification-pending
POST /api/v1/data-quality/disposition-cases/{id}/verify
POST /api/v1/data-quality/disposition-cases/{id}/fail
POST /api/v1/data-quality/disposition-cases/{id}/cancel
```

首个最小实现只需要覆盖 create/list/detail/start/verify/fail/cancel；所有接口均只更新 case 审计记录，不直接修改业务资源。

## 前端体验

- 数据质量复核队列在 `ACCEPTED` 且有处置策略时展示“创建处置 case”。
- 创建后展示 case ID、状态、目标应用入口和执行前检查。
- 详情抽屉展示动作快照、前后复核证据、回滚边界和审计记录。
- `APPLICATION_MANUAL` case 提供跳转目标应用入口；跳转不是执行。
- `OBSERVE_ONLY` / `MIGRATION_REQUIRED` 不展示开始处置按钮，只展示阻断原因和下一步入口。

## 验收证据

最小实现必须至少提供：

- 应用层测试：创建 case、重复创建返回既有 case、处置等级阻断、状态流转、失败记录。
- API 测试：create/list/detail/start/verify/fail/cancel 响应和错误码。
- 前端组件测试：复核队列创建 case、case 列表/详情可见、无直接执行清理按钮。
- `pnpm run typecheck`、`pnpm i18n:lint`、`bash scripts/dev/check-roadmap.sh`、`git diff --check`。
- 静态扫描报告。

## 非目标

- 不实现自动批量清理。
- 不新增 GitLab 远端操作。
- 不把 DRAFT 发布窗口从复核队列直接关闭或删除。
- 不在本 change 中实现 BranchCreationMode 迁移服务。
- 不引入 RBAC、通知或审批流引擎。
