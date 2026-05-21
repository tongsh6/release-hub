# SA-010 发布计划分支状态汇总

日期：2026-05-22

## 用户旅行图

1. 发布经理在发布窗口详情页查看发布计划。
2. 页面除逐仓库展示 feature/release 分支存在性和合并状态外，还能直接看到汇总数字。
3. 当存在 feature 缺失、release 缺失或合并冲突时，页面在发布计划顶部展示风险提示。
4. 用户可以先通过汇总定位风险规模，再查看下方明细定位具体仓库和迭代。

## 实现范围

- `BranchStatusPanel` 新增分支状态汇总：仓库总数、Feature 缺失数、Release 缺失数、已合并数、冲突数。
- `BranchStatusPanel` 在存在缺失或冲突时展示风险提示。
- 补充中英文发布计划汇总和风险提示文案。
- 新增 `BranchStatusPanel.spec.ts`，覆盖分支存在性、合并状态和风险提示计算。
- 清理 `ReleaseWindowDetail.spec.ts` 中未注册 `loading` / `perm` 指令造成的测试噪声。
- 更新 SA-010 矩阵和场景详情，将发布计划分支状态汇总纳入当前覆盖。

## 验证

- `pnpm exec vitest run src/views/release-window/__tests__/BranchStatusPanel.spec.ts src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts`
  - 7 PASS / 0 FAIL
- `pnpm run typecheck`
  - PASS
- `pnpm run lint`
  - PASS
- `bash scripts/dev/static-scan-topn.sh 5`
  - 报告：`.ai/reports/static-scan/20260522-001518/summary.md`
  - `git diff --check`、backend SpotBugs、frontend ESLint、frontend typecheck 均 PASS

## 非目标

- 不新增真实 GitLab 分支状态 E2E 证据。
- 不改变后端 `branch-status` API。
- 不启动完整前后端联调环境。
