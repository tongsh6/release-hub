# 2026-05-23 SA-002 数据质量复核队列页面化

## 背景

SA-002 dry-run 已能生成 188 条待复核动作，并保留应用入口、执行前检查、执行后复核和人工复核决策。但操作者仍需要阅读 Markdown/JSONL，缺少应用内筛选、决策和复核反馈入口。

## 范围

- 扩展 `POST /api/v1/data-quality/cleanup-review`，支持按资源类型、风险类型和复核状态筛选复核结果。
- 新增前端“数据质量复核队列”页面，支持导入 `actions.jsonl`、批量标记人工决策、提交复核并查看摘要。
- 保持 SA-002 安全边界：页面和 API 均不直接执行清理，不修改数据库，不触碰 GitLab。
- 同步更新 data-quality OpenSpec、场景矩阵、项目台账和执行路线图。

## 实现

- 后端 `CleanupReviewCommand` 增加 `resourceTypeFilter`、`riskTypeFilter`、`reviewStatusFilter`。
- `DataQualityCleanupReviewAppService` 在动作复核后按筛选条件返回队列结果，并继续保持 `executionPermitted=false`。
- 前端新增 `frontend/src/views/data-quality/DataQualityReviewQueue.vue` 与 `dataQualityApi`。
- 侧边栏新增“数据质量复核”入口。

## 验证

```bash
mvn -f backend/pom.xml -pl releasehub-application -am -Dtest=DataQualityCleanupReviewAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f backend/pom.xml -pl releasehub-bootstrap -am -Dtest=DataQualityCleanupApiTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/data-quality/__tests__/DataQualityReviewQueue.spec.ts
pnpm run typecheck
pnpm i18n:lint
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- 应用层复核服务：7 PASS / 0 FAIL / 0 SKIP。
- API 复核入口：1 PASS / 0 FAIL / 0 SKIP。
- 前端复核队列组件：2 PASS / 0 FAIL。
- 前端 typecheck 通过。
- i18n lint 通过。
- 静态扫描通过：报告 `.ai/reports/static-scan/20260523-200508/summary.md`；SpotBugs 0，typecheck PASS，frontend lint 仍有 8 个既有 warning。

## 浏览器冒烟

- 启动 Vite dev server 后打开 `/data-quality/review`。
- 通过 Playwright 预置登录态与 `/v1/me` 响应，页面标题为“数据质量复核 - ReleaseHub (Local)”。
- 导入 1 条 `DRAFT_WINDOW_REMAINS` dry-run JSONL，批量标记“批准进入应用入口”并提交受控复核。
- 页面显示已导入 1、已通过 1、待复核 0、已拒绝 0，结果行 `reviewStatus=ACCEPTED` 且“允许执行=否”。

## 结论

SA-002 已从“dry-run 报告和人工复核 API”推进为“应用内复核队列”。当前仍只允许人工复核和进入应用层入口，不允许直接执行或自动执行清理。
