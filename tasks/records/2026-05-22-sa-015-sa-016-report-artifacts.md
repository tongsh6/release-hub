# SA-015/SA-016 发布窗口报告制品化扩展

## 目标

- 补强 SA-015/SA-016 剩余 P2 缺口：发布窗口报告不仅能导出 JSON/CSV，也能导出适合归档和人工审阅的 Markdown 制品。
- 前端发布窗口详情页从单一 CSV 导出入口升级为报告格式菜单，避免隐藏 JSON/Markdown 可用能力。

## 变更

- `ExportAppService` 新增 `exportReleaseWindowMarkdown`，按窗口汇总基础信息、结果分布、Run、RunItem 和 RunStep。
- `ReleaseWindowReportController` 新增 `GET /api/v1/release-windows/{id}/report.md`，返回 `text/markdown`。
- 发布窗口详情页导出入口改为格式菜单，支持 CSV、JSON 和 Markdown。
- `WindowRunApiTest` 覆盖 Markdown 报告端点；`ReleaseWindowDetail.spec.ts` 覆盖三种格式入口。

## 验证

```bash
mvn -q -pl releasehub-bootstrap -am -Dtest=WindowRunApiTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts
mvn -q -DskipTests compile
pnpm run typecheck
pnpm run lint
bash scripts/dev/static-scan-topn.sh 5
```

- 静态扫描报告：`.ai/reports/static-scan/20260522-001959/summary.md`

## 结论

- SA-015/SA-016 发布窗口报告已从结构化 JSON/CSV 扩展到可归档 Markdown 制品；后续若需要更正式交付物，可在同一报告视图基础上继续补 PDF 或制品包。
