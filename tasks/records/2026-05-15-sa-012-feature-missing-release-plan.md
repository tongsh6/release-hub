# 2026-05-15 SA-012 Feature Missing Release Plan

## 任务

按项目台账当前 P1 队列，补 SA-012「feature 缺失」的前端可观察路径，并继续补齐后端/GitLab 强证据。

## 变更

- `frontend/e2e/tests/slice-2-full-flow.spec.ts` 新增 Slice-2 serial 场景：在 UI 创建出的发布窗口、迭代和仓库上下文中模拟 `feature/<iterationKey>` 缺失。
- 新场景断言发布计划面板展示 feature 分支名、缺失状态、对应 release 分支、计划顺序和待合并状态。
- `scripts/acceptance/run-acceptance.sh` 新增 SA-012 5.3：通过真实 GitLab API 创建后删除本轮唯一 feature 分支，复核 GitLab 直查、`branch-status` 和 Orchestrate RunStep 的缺失证据。
- `scripts/acceptance/run-acceptance.sh` 新增 SA-012 5.4：通过真实 GitLab API 预置本轮唯一 release 分支，复核 GitLab 直查、`branch-status` 和 Attach RunStep 的 `BRANCH_EXISTS` 证据。
- `run-acceptance.sh` 的 SA-010 release 分支直查从硬编码 GitLab project id 改为按仓库 `cloneUrl` 推导项目路径，避免持久化 GitLab 下误报。
- `docs/reports/scenario-acceptance-matrix.md` 和 `docs/project-ledger.md` 已同步 SA-012 最新证据。

## 验证

- `pnpm --dir frontend run typecheck`：通过。
- `pnpm --dir frontend i18n:lint`：通过。
- `pnpm --dir frontend exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"`：通过，8 tests。
- `scripts/dev/static-scan-topn.sh 10`：通过，报告 `.ai/reports/static-scan/20260515-144113/summary.md`；TopN 未发现代码问题。
- `bash -n scripts/acceptance/run-acceptance.sh`：通过。
- `bash scripts/acceptance/run-acceptance.sh`：通过，68 PASS / 0 FAIL / 0 SKIP；新增 5.3 断言 GitLab feature 不存在、`branch-status` feature 缺失且 release 存在、Orchestrate `ENSURE_FEATURE/SKIPPED`；新增 5.4 断言 GitLab release 预置存在、`branch-status` release/feature 均存在、Attach `ENSURE_RELEASE/BRANCH_EXISTS`。

## 后续

- SA-012 feature 缺失已具备前端观察和后端/GitLab 强证据。
- SA-012 release 分支已存在已具备前端观察和后端/GitLab 强证据。
- SA-015 仍保留冲突详情和部分失败复核旅程。
