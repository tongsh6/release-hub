# E2E TypeScript 基线修复

## 目标

- 让 `frontend/e2e` Playwright specs 可以通过 raw TypeScript 检查。
- 避免新增 Playwright spec 只能依赖 `--list` 发现来验证基本语法和类型。
- 明确 `--list` 和 TypeScript 检查只属于可运行性检查，不属于场景化验收测试通过证据。

## 变更

- `frontend/e2e/tsconfig.json` 增加 `DOM` lib，覆盖 `document` 和 `HTMLElement` 类型。
- E2E specs 的 shared helper imports 改为 NodeNext 兼容的 `.js` specifier。

## 验证

```bash
pnpm exec tsc -p e2e/tsconfig.json --noEmit
pnpm exec playwright test e2e/tests/version-policy.spec.ts --list
pnpm exec playwright test e2e/tests/branch-rule.spec.ts --list
pnpm run typecheck
pnpm run lint
bash scripts/dev/static-scan-topn.sh 5
```

- 静态扫描报告：`.ai/reports/static-scan/20260522-005336/summary.md`

## 结论

- E2E 自动化现在多了一层可运行的 TypeScript 静态验证；后续新增 Playwright spec 仍需外部 Playwright 实跑真实页面后，才能计入场景化验收。
