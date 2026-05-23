# 2026-05-23 SA-002 验收数据命名空间与保留策略

## 背景

SA-002 已经具备 dry-run 报告、人工复核 API 和应用内复核队列，但报告里的动作仍主要按风险类型和资源类型呈现。发布经理无法在页面里直接区分本轮验收资产、历史验收资产、用户业务资产或无法归因的历史资产，后续全量验收会继续把 DRAFT 残留数量当成主要噪音。

## 范围

- 为 SA-002 dry-run 动作增加 `dataNamespace`、`reviewBatchId`、`assetScope` 和 `retentionPolicy`。
- 在 `summary.md`、`actions.md`、`actions.jsonl` 中输出命名空间、批次、资产范围和保留策略口径。
- 复核 API 保留这些元数据，并支持按 `assetScope` 筛选。
- 数据质量复核队列页面展示命名空间、复核批次、资产范围和保留策略，并可按资产范围筛选。
- 保持安全边界：不直接删除数据库记录，不关闭发布窗口，不触碰 GitLab 远端资源，不绕过应用层不变量。

## 实现

- `scripts/acceptance/sa002-safe-cleanup.sh` 新增默认口径：
  - `SA002_DATA_NAMESPACE=acceptance`
  - `SA002_REVIEW_BATCH_ID=sa002-<timestamp>`
  - `SA002_CURRENT_BATCH_MARKER=<timestamp>`
  - `SA002_RETENTION_POLICY=manual-review-then-archive`
- dry-run 动作增加资产范围分类：
  - `CURRENT_BATCH`：可由当前批次标识识别的本轮资产。
  - `HISTORICAL_ACCEPTANCE`：命名或来源显示为历史验收资产。
  - `USER_BUSINESS`：命名显示为用户业务资产。
  - `UNKNOWN_LEGACY`：无法从命名推断的历史资产。
- `CleanupActionInput` / `CleanupActionReview` 扩展命名空间元数据；`CleanupReviewCommand` 增加 `assetScopeFilter`。
- `POST /api/v1/data-quality/cleanup-review` 保留元数据并按资产范围筛选。
- `DataQualityReviewQueue.vue` 展示命名空间、复核批次、资产范围、保留策略，并提供资产范围筛选。

## 验证

```bash
mvn -f backend/pom.xml -pl releasehub-application -am -Dtest=DataQualityCleanupReviewAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f backend/pom.xml -pl releasehub-bootstrap -am -Dtest=DataQualityCleanupApiTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/data-quality/__tests__/DataQualityReviewQueue.spec.ts
bash -n scripts/acceptance/sa002-safe-cleanup.sh
pnpm run typecheck
pnpm i18n:lint
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- 应用层复核服务：8 PASS / 0 FAIL / 0 SKIP。
- API 复核入口：1 PASS / 0 FAIL / 0 SKIP。
- 前端复核队列组件：2 PASS / 0 FAIL。
- `sa002-safe-cleanup.sh` 语法检查通过。
- 前端 typecheck 通过。
- i18n lint 通过。
- 静态扫描通过：报告 `.ai/reports/static-scan/20260523-203458/summary.md`；SpotBugs 0，frontend lint PASS，typecheck PASS。

## 浏览器冒烟

- 启动 Vite dev server 后打开 `/data-quality/review`。
- 通过 Playwright 预置登录态、`/v1/me` 和 `/v1/data-quality/cleanup-review` 响应。
- 导入 1 条带 `dataNamespace=acceptance`、`reviewBatchId=sa002-20260523`、`assetScope=HISTORICAL_ACCEPTANCE`、`retentionPolicy=manual-review-then-archive` 的 dry-run JSONL。
- 提交复核后，页面展示“数据命名空间 / 复核批次 / 资产范围 / 保留策略”列，并在结果行显示 `HISTORICAL_ACCEPTANCE`、`manual-review-then-archive` 和“允许执行=否”。

## 结论

SA-002 数据质量复核已从“动作清单”推进到“可按命名空间、批次、资产范围和保留策略复核”。这仍然是只读 dry-run 与人工复核闭环，不执行自动清理。
