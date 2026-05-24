# 2026-05-24 SA-002 BranchCreationMode 迁移服务 proposal 评审门禁

## 背景

BranchCreationMode 独立迁移服务 proposal 已形成。根据执行路线图和 OpenSpec 规则，实现前必须先评审迁移范围、候选分类、dry-run 优先级、回滚计划和验收证据。

## 评审结论

结论：`APPROVE_DRY_RUN_ONLY`。

批准进入下一切片：SA-002 BranchCreationMode 最小 dry-run 实现。

不批准进入执行器、迁移 API、数据库写入、迁移审计表、前端执行入口或 GitLab 远端操作。

## 评审依据

- proposal 明确只处理历史 `iteration_repo.branch_creation_mode` 风险。
- design 明确不修改 `feature_branch`、版本字段、发布窗口、迭代仓库集合、数据质量 case 或 GitLab 远端资源。
- data-quality delta spec 明确复核队列和处置 case 不得直接执行 BranchCreationMode 迁移。
- 现有 `sa002-safe-cleanup.sh` 已具备只读扫描基础。
- 现有 `DataQualityCleanupReviewAppService` 已把该风险标记为 `MIGRATION_REQUIRED`。

## 下一切片验收标准

- 只读扫描历史 `iteration_repo.branch_creation_mode` 缺失、空白、可规范化或非法值。
- 输出四类候选：`SAFE_AUTO_DEFAULTABLE`、`NORMALIZE_LEGAL_VALUE`、`MANUAL_MAPPING_REQUIRED`、`NOT_MIGRATABLE_IN_THIS_SERVICE`。
- 输出 Markdown/JSON dry-run 报告，包含统计、推断依据、执行计划草案和拒绝原因。
- 应用层测试证明不写数据库、不触碰 GitLab、不修改业务字段。
- 同步场景矩阵、项目台账、路线图和任务记录。

## 验证

```bash
bash scripts/dev/check-roadmap.sh
git diff --check
cd frontend && pnpm run typecheck
cd frontend && pnpm i18n:lint
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- `bash scripts/dev/check-roadmap.sh` 通过，唯一 HEAD 指向 SA-002 最小 dry-run 实现。
- `git diff --check`、`cd frontend && pnpm run typecheck`、`cd frontend && pnpm i18n:lint` 均通过。
- 静态扫描通过：`.ai/reports/static-scan/20260524-160645/summary.md`；SpotBugs 0，frontend lint PASS，frontend typecheck PASS。

## 结论

proposal 评审门禁已完成，下一 HEAD 转向最小 dry-run 实现；真实执行与数据库写入继续暂缓。
