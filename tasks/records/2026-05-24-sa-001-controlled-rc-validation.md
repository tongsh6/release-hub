# 2026-05-24 SA-001 受控发布候选 dogfood/staging 验证

## 背景

当前路线图队首为 SA-001 受控发布候选 dogfood/staging 验证。目标不是继续扩大 Phase 2 功能范围，而是按发布候选报告复核候选版本在受控环境中的真实运行证据：后端/GitLab 强证据、前端用户旅程、发布候选评审页、数据质量只读复核队列、Run/窗口详情和停止/回滚边界。

## 本轮发现

- 全量后端/真实 GitLab 场景验收通过，结果为 170 PASS / 0 FAIL / 0 SKIP。
- 首轮完整前端 E2E 暴露 2 个稳定失败和 1 个 flaky：
  - 版本更新策略继承用例没有覆盖当前详情页并行加载 `parallel-scope` 的接口形态，导致版本更新入口不可见。
  - Slice-1 窗口详情断言仍用 `.el-descriptions().last()`，新增“同组并行发布窗口”后命中了并行窗口摘要而不是基本信息区。
  - Slice-2 MOCK provider 下拉项在完整并发套件中可能命中离视口的 Element Plus option。
- 这些问题属于受控验证应暴露的前端验收路径稳定性问题，不是产品功能扩展。

## 变更

| 文件 | 类型 | 说明 |
|---|---|---|
| `frontend/e2e/tests/version-update-policy.spec.ts` | E2E | 路由 stub 支持 `/api/v1` 与 `/v1` 两种运行形态；补齐 `/parallel-scope`、`iterationKey` 和鉴权用户；用真实登录建立稳定路由守卫状态 |
| `frontend/e2e/tests/slice-1-group-window.spec.ts` | E2E | 窗口详情断言改为基本信息区，避免新增并行窗口摘要后误判 |
| `frontend/e2e/tests/slice-2-full-flow.spec.ts` | E2E | MOCK provider 选择改为点击当前 Element Plus 下拉项本体，避免并发完整套件中离视口 option 抖动 |

## 验证

```bash
bash scripts/acceptance/run-acceptance.sh
pnpm exec playwright test e2e/tests/version-update-policy.spec.ts
pnpm exec playwright test e2e/tests/version-update-policy.spec.ts e2e/tests/slice-1-group-window.spec.ts e2e/tests/slice-2-full-flow.spec.ts
pnpm run test:e2e
pnpm exec vitest run src/views/run/__tests__/RunDetail.spec.ts src/views/data-quality/__tests__/DataQualityReviewQueue.spec.ts src/views/release-governance/__tests__/ReleaseCandidateReview.spec.ts src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts src/views/release-window/__tests__/VersionUpdateDialog.spec.ts
pnpm run typecheck
pnpm i18n:lint
bash scripts/dev/check-roadmap.sh
git diff --check
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- 全量场景验收：170 PASS / 0 FAIL / 0 SKIP。
- 版本策略专项：1 PASS / 0 FAIL。
- 前端三组重点旅程：37 PASS / 0 FAIL。
- 前端完整 E2E：49 PASS / 0 FAIL。
- 关键页面组件回归：21 PASS / 0 FAIL。
- typecheck、i18n lint、roadmap 检查和 `git diff --check` 均通过。
- 静态扫描通过：`.ai/reports/static-scan/20260524-144034/summary.md`；SpotBugs 0，frontend lint PASS，frontend typecheck PASS。

## 结论

SA-001 受控发布候选 dogfood/staging 验证已完成：候选版本可在受控环境继续推进，且验证过程中暴露的前端用户旅程稳定性问题已修复并复跑通过。

数据质量风险仍按既定边界处理：历史 DRAFT / attach 残留只读可见，不自动删除数据库记录、不关闭发布窗口、不触碰 GitLab 远端资源。下一队首应转向 SA-002 数据质量人工复核处置策略，把“可见、可筛选、可签核”继续推进为“人工复核后如何受控处置”的产品方案与验收出口。
