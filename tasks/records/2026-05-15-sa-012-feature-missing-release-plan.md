# 2026-05-15 SA-012 Feature Missing Release Plan

## 任务

按项目台账当前 P1 队列，补 SA-012「feature 缺失」的前端可观察路径。

## 变更

- `frontend/e2e/tests/slice-2-full-flow.spec.ts` 新增 Slice-2 serial 场景：在 UI 创建出的发布窗口、迭代和仓库上下文中模拟 `feature/<iterationKey>` 缺失。
- 新场景断言发布计划面板展示 feature 分支名、缺失状态、对应 release 分支、计划顺序和待合并状态。
- `docs/reports/scenario-acceptance-matrix.md` 和 `docs/project-ledger.md` 已同步 SA-012 最新证据。

## 验证

- `pnpm --dir frontend run typecheck`：通过。
- `pnpm --dir frontend i18n:lint`：通过。
- `pnpm --dir frontend exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"`：通过，8 tests。
- `scripts/dev/static-scan-topn.sh 10`：通过，报告 `.ai/reports/static-scan/20260515-144113/summary.md`；TopN 未发现代码问题。

## 后续

- SA-012 feature 缺失仍需补对应后端/GitLab 强证据。
- SA-015 仍保留冲突详情和部分失败复核旅程。
