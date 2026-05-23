# 2026-05-23 SA-002 验收脚本与应用 API 数据源口径统一

## 背景

SA-002 已经具备 dry-run 报告、人工复核 API、应用内复核队列、验收数据命名空间和保留策略。但全量验收、safe-cleanup summary 与复核页面仍可能分别使用“应用 API 资产统计”“数据库直查资产统计”“复核动作数量”等不同口径，操作者容易把底层审计总量误读为用户可见风险。

## 范围

- 统一三类数据源口径：
  - `API_VISIBLE_ASSETS`：通过应用 API 可见的分组、仓库、发布窗口、迭代和 Run 资产。
  - `DB_AUDIT_ASSETS`：数据库只读审计资产，用于发现底层字段风险，不代表用户当前可见风险总量。
  - `REVIEW_QUEUE_ACTIONS`：进入人工复核队列的 dry-run 动作。
- 全量验收输出使用 `API_VISIBLE_ASSETS` 标注用户可见资产统计。
- safe-cleanup summary 输出数据源口径表和资产统计表。
- 复核 API 返回资产边界说明和资产范围计数。
- 数据质量复核队列页面展示数据源口径边界和资产范围计数。
- 保持安全边界：不直接删除数据库记录，不关闭发布窗口，不触碰 GitLab 远端资源，不绕过应用层不变量。

## 实现

- `CleanupReviewResult` 新增 `assetBoundaries` 和 `assetScopeCounts`：
  - `assetBoundaries` 固定说明 `API_VISIBLE_ASSETS`、`DB_AUDIT_ASSETS`、`REVIEW_QUEUE_ACTIONS`。
  - `assetScopeCounts` 按匹配后的复核动作统计 `assetScope` 数量。
- `DataQualityReviewQueue.vue` 新增“数据源口径”结果区，展示资产边界说明和资产范围计数。
- `scripts/acceptance/run-acceptance.sh` 将全量验收资产统计标注为 `API_VISIBLE_ASSETS`。
- `scripts/acceptance/sa002-safe-cleanup.sh` 在 `summary.md` 中输出数据源口径表和资产统计表，分别列出 API 可见资产、数据库审计资产和复核队列动作。
- OpenSpec、场景矩阵、项目台账、路线图和脚本索引同步记录该口径。

## 验证

```bash
mvn -f backend/pom.xml -pl releasehub-application -am clean test -Dtest=DataQualityCleanupReviewAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -f backend/pom.xml -pl releasehub-bootstrap -am -Dtest=DataQualityCleanupApiTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/data-quality/__tests__/DataQualityReviewQueue.spec.ts
bash -n scripts/acceptance/sa002-safe-cleanup.sh
bash -n scripts/acceptance/run-acceptance.sh
pnpm run typecheck
pnpm i18n:lint
bash scripts/dev/check-roadmap.sh
git diff --check
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- 应用层复核服务：8 PASS / 0 FAIL / 0 SKIP；验证 `assetBoundaries` 固定返回三类数据源口径，并按 `assetScope` 统计复核动作数量。
- API 复核入口：1 PASS / 0 FAIL / 0 SKIP；响应返回资产边界说明、资产范围计数和动作元数据，所有结果仍 `executionPermitted=false`。
- 前端复核队列组件：2 PASS / 0 FAIL。
- `sa002-safe-cleanup.sh` 和 `run-acceptance.sh` 语法检查通过。
- 前端 typecheck 通过。
- i18n lint 通过。
- roadmap 检查通过：HEAD 唯一且指向 SA-015。
- `git diff --check` 通过。
- 静态扫描通过：报告 `.ai/reports/static-scan/20260523-204621/summary.md`；SpotBugs 0，frontend lint PASS，typecheck PASS。

## 浏览器冒烟

- 启动 Vite dev server 后打开 `/data-quality/review`。
- 通过 Playwright 预置登录态、`/v1/me` 和 `/v1/data-quality/cleanup-review` 响应。
- 导入 1 条带 `dataNamespace=acceptance`、`reviewBatchId=sa002-20260523`、`assetScope=HISTORICAL_ACCEPTANCE`、`retentionPolicy=manual-review-then-archive` 的 dry-run JSONL。
- 提交复核后，页面展示“数据源口径”、`API_VISIBLE_ASSETS`、`DB_AUDIT_ASSETS`、`REVIEW_QUEUE_ACTIONS` 和 `HISTORICAL_ACCEPTANCE: 1`。

## 结论

SA-002 已从“可按命名空间和资产范围复核”推进到“验收脚本、safe-cleanup 报告和应用复核队列使用同一数据源边界”。这仍然是只读 dry-run 与人工复核闭环，不执行自动清理。
