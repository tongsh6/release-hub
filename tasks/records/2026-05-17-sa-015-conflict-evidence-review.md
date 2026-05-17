# 2026-05-17 SA-015 Conflict Evidence Review

## 任务

按项目台账当前 P1 队列，补 SA-015「测试人员在窗口详情复核冲突证据」旅程。

## 变更

- Playwright Slice-2 serial 旅程新增 SA-015 断言：复用 UI 创建出的发布窗口、迭代和仓库，从窗口详情复核 `MERGE_CONFLICT`、`BRANCH_NONCOMPLIANT`、`CROSS_REPO_VERSION_MISMATCH` 的类型分布、分支/版本详情、建议处理方式和外部处理语义。
- 断言复核路径不会误触发 `resolve-conflict` 版本同步接口。
- `scenario-acceptance-matrix.md` 和 `project-ledger.md` 已同步 SA-015 最新状态：冲突详情复核已补，剩余 P1 缺口收敛为部分失败复核。

## 验证

- `pnpm --dir frontend run typecheck`：通过。
- `pnpm --dir frontend i18n:lint`：通过。
- `pnpm --dir frontend exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"`：通过，10 passed / 0 failed。
- `scripts/dev/static-scan-topn.sh 10`：通过，报告 `.ai/reports/static-scan/20260517-202603/summary.md`；diff check、SpotBugs、frontend typecheck 均 PASS。frontend lint 仅有 4 个既有 warning，TopN 未发现代码问题。

## 后续

- SA-015 仍保留部分失败复核旅程。
- SA-010/SA-011 仍需更多真实冲突类型后端/GitLab 强证据。
