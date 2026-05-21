# SA-007 版本更新策略选择 UI 回归

## 目标

- 补齐 SA-007 “版本更新入口按组织/仓库范围选取默认策略”的 UI 回归覆盖。
- 验证 `VersionUpdateDialog` 在给定页面数据下加载可继承策略并推导目标版本。
- 该记录不声明场景化验收通过；完整验收必须由外部 Playwright 驱动真实页面并连接真实后端完成。

## 变更

- 新增 `frontend/e2e/tests/version-update-policy.spec.ts`。
- 通过路由级 API stub 构造 UI 状态：
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

- Playwright 浏览器执行：1 passed。
- 静态扫描报告：`.ai/reports/static-scan/20260522-010122/summary.md`

## 限制

- 该用例使用 route-level API stub，可在仅启动 Vite 的情况下验证前端交互；它不是场景化验收测试通过证据。
- 完整验收仍待环境就绪后，由外部 Playwright 驱动真实页面、真实后端和真实数据旅程完成。

## 结论

- SA-007 版本更新策略选择已有 UI 回归证据；真实页面场景化验收仍待补齐。
