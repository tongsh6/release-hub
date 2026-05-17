# 2026-05-17 SA-015 Partial Failure Review

## 任务

按项目台账当前 P1 队列，补 SA-015「测试人员复核部分失败 Run」旅程。

## 变更

- Playwright Slice-2 serial 旅程新增 SA-015 断言：复用 UI 创建出的发布窗口标识，从 Run 列表筛出部分失败 Run。
- Run 详情页断言同一个 Run 内成功仓库项和失败仓库项并存，失败项展示 `MERGE_BLOCKED`、失败任务重试次数和错误信息。
- `scenario-acceptance-matrix.md` 和 `project-ledger.md` 已同步 SA-015 最新状态：失败 Run、分组筛选、窗口详情冲突证据和 Run 详情部分失败前端复核均已补。

## 验证

- `pnpm --dir frontend run typecheck`：通过。
- `pnpm --dir frontend i18n:lint`：通过。
- `pnpm --dir frontend exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"`：通过，11 passed / 0 failed。
- `scripts/dev/static-scan-topn.sh 10`：通过，报告 `.ai/reports/static-scan/20260517-204030/summary.md`；diff check、SpotBugs、frontend lint、frontend typecheck 均 PASS。

## 后续

- 真实部分失败生成、失败重试和发布报告导出归入 SA-010/SA-016 扩展。
- SA-010/SA-011 仍需更多真实冲突类型后端/GitLab 强证据。
