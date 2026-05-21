# E2E TypeScript 基线修复

## 目标

- 让 `frontend/e2e` Playwright specs 可以通过 raw TypeScript 检查。
- 避免新增 E2E spec 只能依赖 Playwright `--list` 发现来验证基本语法和类型。

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

- E2E 自动化现在多了一层可运行的 TypeScript 静态验证，后续新增 Playwright spec 不再只能依赖 `--list` 发现。
