# Review: BranchCreationMode 独立迁移服务 proposal

## 评审结论

结论：`APPROVE_DRY_RUN_ONLY`。

允许进入下一切片：最小 dry-run 实现。

不批准进入：

- 执行 API。
- 数据库写入。
- 迁移审计表。
- 前端执行入口。
- GitLab 远端查询或变更。

## 评审依据

- proposal 已明确 BranchCreationMode 风险仍为 `MIGRATION_REQUIRED`，不能从数据质量复核队列或处置 case 直接执行。
- 迁移范围已限定为历史 `iteration_repo.branch_creation_mode` 缺失、空白、可规范化或非法值。
- 设计已明确排除 `feature_branch`、版本字段、发布窗口、迭代仓库集合、数据质量 case 和 GitLab 远端资源。
- 现有 `scripts/acceptance/sa002-safe-cleanup.sh` 已具备只读扫描基础，可作为 dry-run 候选识别口径参考。
- 现有 `DataQualityCleanupReviewAppService` 已把 `BRANCH_CREATION_MODE_MISSING_OR_INVALID` 标记为 `MIGRATION_REQUIRED`，产品边界与 proposal 一致。

## 批准条件

下一切片只能交付只读 dry-run：

- 新增应用层 dry-run 服务和只读端口，扫描 `iteration_repo.branch_creation_mode` 风险。
- 输出 `SAFE_AUTO_DEFAULTABLE`、`NORMALIZE_LEGAL_VALUE`、`MANUAL_MAPPING_REQUIRED`、`NOT_MIGRATABLE_IN_THIS_SERVICE` 分类。
- 输出 Markdown/JSON 报告，包含候选统计、推断依据、执行计划草案和拒绝原因。
- 补应用层测试，证明 dry-run 不写数据库、不触碰 GitLab、不修改业务字段。
- 同步任务记录、场景矩阵、项目台账和路线图。

## 必须暂缓

- `approve-plan`、`execute`、`verify`、`rollback-plan` API 暂缓。
- 迁移批次持久化表暂缓。
- UI 入口暂缓。
- 真实数据库写入暂缓。

## 下一 HEAD

`SA-002 BranchCreationMode 最小 dry-run 实现`。
