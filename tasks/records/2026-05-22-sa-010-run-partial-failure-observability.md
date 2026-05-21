# SA-010 Run 部分失败复核与重试可观察性

日期：2026-05-22

## 用户旅行图

1. 发布经理或测试人员打开一次发布相关 Run 的详情页。
2. 同一 Run 内既有成功执行项又有失败执行项时，页面直接展示部分失败提示。
3. 页面汇总执行项总数、成功数、失败数和可重试数，降低用户从表格逐行推断状态的成本。
4. 用户仍然只能重试失败执行项，成功执行项不会被再次提交。

## 实现范围

- Run 详情基础信息卡新增执行项汇总：总数、成功数、失败数、可重试数。
- Run 详情在成功与失败执行项并存时展示部分失败提示。
- Run 任务区域新增任务总数、失败数、可重试数摘要。
- 补齐英文 `run.detail.tasksTitle`、Run task 状态/类型等 i18n 文案，避免英文环境显示裸 key。
- `RunDetail.spec.ts` 新增部分成功/失败、任务失败和可重试计数断言，并清理测试中的 directive warning。
- 更新 SA-010 矩阵和场景详情，将部分成功/失败与失败项重试前端复核纳入当前覆盖。

## 验证

- `pnpm exec vitest run src/views/run/__tests__/RunDetail.spec.ts`
  - 4 PASS / 0 FAIL
- `pnpm run typecheck`
  - PASS
- `pnpm run lint`
  - PASS
- `bash scripts/dev/static-scan-topn.sh 5`
  - 报告：`.ai/reports/static-scan/20260522-000911/summary.md`
  - `git diff --check`、backend SpotBugs、frontend ESLint、frontend typecheck 均 PASS

## 非目标

- 不改变后端 Run retry 语义。
- 不新增真实 GitLab 分支状态 E2E 证据。
- 不启动完整前后端联调环境。
