# 2026-05-23 SA-016 发布报告制品包归档

## 背景

SA-016 已具备关闭窗口、关闭后关键操作拒绝、重复关闭幂等、收尾 Run 可见、CI 触发状态和发布报告 JSON/CSV/Markdown 导出证据。路线图当前 HEAD 要求补齐“发布报告制品包归档”，让发布证据可以作为单个可审计制品下载和归档。

## 范围

- 新增发布窗口报告 ZIP 制品包下载端点。
- 制品包固定包含 `manifest.txt`、`report.json`、`report.csv`、`report.md`。
- 前端发布窗口详情页导出菜单新增“制品包”入口。
- 保持既有 JSON、CSV、Markdown 单文件报告端点和前端入口兼容。

## 非目标

- 不做 PDF 生成。
- 不做 CI 深集成或远程制品库上传。
- 不改变发布收尾、关闭窗口、CI 触发状态和 retry 语义。
- 不引入新的全局工具、系统级依赖或外部归档服务。

## 改动

- `ReleaseWindowReportController` 新增 `GET /api/v1/release-windows/{id}/report.zip`：
  - `Content-Type` 为 `application/zip`。
  - `Content-Disposition` 使用 `release-window-<windowKey>-evidence.zip`。
  - ZIP 内固定写入 manifest、JSON、CSV、Markdown 四个文件。
- `WindowRunApiTest` 解包验证制品包内容：
  - manifest 包含窗口 ID、窗口 Key、文件清单。
  - `report.json`、`report.csv`、`report.md` 均对应同一窗口证据。
  - 夹具显式让内存 feature 分支版本不可抽取，保持该 MockMvc 旅程的“干净窗口编排”语义，避免版本预检挡住报告导出断言。
- `ReleaseWindowDetail.vue` 导出菜单新增制品包格式。
- 中英文 i18n 新增导出项文案。
- `release-window` OpenSpec 补充发布窗口报告制品包归档契约。

## 验证

```bash
mvn -pl releasehub-bootstrap -am -Dtest=WindowRunApiTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts
pnpm run typecheck
pnpm i18n:lint
git diff --check
bash scripts/dev/check-roadmap.sh
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- `WindowRunApiTest`：1 PASS / 0 FAIL / 0 SKIP。
- `ReleaseWindowDetail.spec.ts`：8 PASS / 0 FAIL / 0 SKIP。
- 前端 typecheck、i18n lint、路线图检查和最终静态扫描均通过。
- 静态扫描报告：`.ai/reports/static-scan/20260523-134733/summary.md`。

## 结论

SA-016 发布报告已从单文件 JSON/CSV/Markdown 扩展到可归档 ZIP 制品包。当前制品包归档缺口闭环；路线图下一 HEAD 转向 SA-002 存量数据安全清理。
