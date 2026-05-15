# 2026-05-15 SA-011 Merge Conflict Risk Detail

## 任务

按项目台账当前 P1 队列，补 SA-010/SA-011「更多真实冲突类型详情」中的 `MERGE_CONFLICT` 前端观察路径。

## 变更

- `frontend/e2e/tests/slice-2-full-flow.spec.ts` 新增 Slice-2 serial 场景：在 UI 创建出的发布窗口、迭代和仓库上下文中模拟 `MERGE_CONFLICT` + `MISMATCH` 风险报告。
- 新场景断言冲突面板展示类型分布、合并冲突源/目标分支、阻断级别、建议处理方式和 Git 平台处理入口。
- 新场景确认合并冲突不会误走版本同步解决接口。
- `docs/reports/scenario-acceptance-matrix.md` 和 `docs/project-ledger.md` 已同步 SA-011 最新证据。

## 验证

- `pnpm --dir frontend run typecheck`：通过。
- `pnpm --dir frontend i18n:lint`：通过。
- `pnpm --dir frontend exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"`：通过，7 tests。
- `scripts/dev/static-scan-topn.sh 10`：通过，报告 `.ai/reports/static-scan/20260515-143024/summary.md`；TopN 未发现代码问题。

## 后续

- SA-010/SA-011 仍保留更多真实冲突类型详情和部分失败重试。
- SA-012 仍保留 feature 缺失等路径及对应后端/GitLab 强证据。
