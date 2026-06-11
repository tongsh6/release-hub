# 2026-05-24 SA-002 数据质量处置 case 场景验收与证据归档

## 背景

SA-002 已完成处置 case 最小实现，但仍需要从真实页面旅程证明操作者可以完成 dry-run 动作导入、人工复核、创建 case、查看详情和记录状态，而不是只依赖 API 或组件测试。

## 范围

- 新增外部 Playwright 场景 `frontend/e2e/tests/data-quality-disposition-case.spec.ts`。
- 旅程从 `/data-quality/review` 页面开始：
  - 粘贴一条 `DRAFT_WINDOW_REMAINS` dry-run JSONL。
  - 批量标记批准进入应用入口并提交复核。
  - 从复核结果创建处置 case。
  - 打开 case 详情，记录执行前快照、开始人工处置、记录执行后复核。
  - 通过后置 API 复核 case 状态、来源报告、资源、风险、操作者和前后快照。
- E2E helper 的预置用户补齐 `data-quality:review` 权限，便于未来启用前端权限守卫时保持旅程有效。

## 边界

- 页面验收不使用业务 API route stub。
- API 查询只作为页面旅程后的证据复核，不替代用户操作。
- 页面断言没有直接清理或自动清理按钮。
- 处置 case 仍只记录审计状态，不自动删除数据库记录、不自动关闭发布窗口、不迁移业务数据、不触碰 GitLab 远端资源。

## 验证

```bash
scripts/dev/start-local-env.sh hold
pnpm exec playwright test e2e/tests/data-quality-disposition-case.spec.ts
pnpm exec tsc -p e2e/tsconfig.json --noEmit
pnpm exec vitest run src/views/data-quality/__tests__/DataQualityReviewQueue.spec.ts
pnpm run typecheck
pnpm i18n:lint
bash scripts/dev/check-roadmap.sh
git diff --check
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- SA-002 外部 Playwright 页面验收通过：1 PASS / 0 FAIL。
- E2E TypeScript 检查通过。
- 数据质量复核队列组件测试通过：3 PASS / 0 FAIL。
- 前端 typecheck 通过。
- i18n lint 通过。
- roadmap 检查通过，唯一 HEAD 指向 SA-001 发布候选交付证据收口与人工评审准备。
- `git diff --check` 通过。
- 静态扫描通过：`.ai/reports/static-scan/20260524-154130/summary.md`；SpotBugs 0，frontend lint PASS，frontend typecheck PASS。

## 结论

SA-002 数据质量处置 case 已从最小实现推进到真实页面场景验收。当前能力可以作为受控发布候选的数据质量治理证据，但仍不进入自动清理、自动关闭窗口或批量迁移阶段。
