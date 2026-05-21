# SA-008 发布窗口冻结限制前端证据

日期：2026-05-21

## 用户旅行图

1. 发布经理在发布窗口列表或详情页查看一个 DRAFT 窗口。
2. 未冻结时，可以看到关联迭代、冻结等发布计划配置入口。
3. 冻结后，列表和详情页不再展示关联迭代、解除关联、代码合并等发布计划变更入口。
4. 冻结后只保留解冻入口，用于显式恢复配置能力。

## 实现范围

- 发布窗口列表把“关联迭代”和“冻结”按钮抽成状态判断函数，并在 `frozen=true` 时隐藏计划变更入口。
- 发布窗口详情页代码合并按钮复用 `canChangeIterations`，冻结时与关联/解除关联入口保持一致隐藏。
- ReleaseWindowList Vitest 覆盖冻结草稿与未冻结草稿的入口可见性规则。
- ReleaseWindowDetail Vitest 覆盖冻结草稿隐藏关联、解除关联和代码合并入口，并保留解冻入口。
- 更新 SA-008 矩阵和场景详情，将组织筛选、组织路径和冻结限制证据纳入当前覆盖。

## 验证

- `pnpm exec vitest run src/views/release-window/__tests__/ReleaseWindowList.spec.ts src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts src/views/release-window/__tests__/ConflictPanel.spec.ts`
  - 11 PASS / 0 FAIL
- `pnpm run typecheck`
  - PASS
- `pnpm run lint`
  - PASS
- `bash scripts/dev/static-scan-topn.sh 5`
  - 报告：`.ai/reports/static-scan/20260521-234440/summary.md`
  - `git diff --check`、backend SpotBugs、frontend ESLint、frontend typecheck 均 PASS

## 非目标

- 不改变后端冻结语义。
- 不补删除保护。
- 不启动全链路 Playwright 环境。
