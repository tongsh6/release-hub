# SA-007 版本更新策略选择 UI E2E

## 目标

- 补齐 SA-007 “版本更新入口按组织/仓库范围选取默认策略”的 UI 旅程自动化。
- 验证真实 `VersionUpdateDialog` 在发布窗口详情页中加载可继承策略并推导目标版本。

## 变更

- 新增 `frontend/e2e/tests/version-update-policy.spec.ts`。
- 通过路由级 API stub 构造：
  - 发布窗口详情。
  - 关联迭代和仓库。
  - 仓库当前版本。
  - applicable policies：子项目策略优先、全局策略兜底。
  - validate response：不同策略推导不同目标版本。
- 用例覆盖：
  - 打开版本更新弹窗。
  - 看到仓库和可继承策略。
  - 默认最具体策略推导 `1.3.0`。
  - 切换全局策略后重新推导 `1.2.4`。

## 验证

```bash
pnpm exec tsc -p e2e/tsconfig.json --noEmit
pnpm exec playwright test e2e/tests/version-update-policy.spec.ts
pnpm run typecheck
pnpm run lint
bash scripts/dev/static-scan-topn.sh 5
```

- Playwright 浏览器实跑：1 passed。
- 静态扫描报告：`.ai/reports/static-scan/20260522-010122/summary.md`

## 限制

- 该用例使用 route-level API stub，可在仅启动 Vite 的情况下验证前端旅程；完整后端真实数据浏览器实跑仍待环境就绪。

## 结论

- SA-007 版本更新策略选择已有 UI 旅程浏览器证据。
