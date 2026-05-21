# SA-007 版本策略真实页面候选旅程

## 目标

- 补齐 SA-007 scoped policy 创建、编辑、删除的外部 Playwright 候选旅程。
- 明确该用例只有在真实前端、真实后端和真实数据链路上实跑通过后，才可计入场景化验收测试。

## 变更

- 新增 `frontend/e2e/tests/version-policy.spec.ts`：
  - 创建项目级版本策略，并验证项目作用域必填校验。
  - 编辑策略为子项目作用域，并验证项目、子项目和递增规则展示。
  - 删除清理测试策略。
- 复用现有 E2E helper：`ensureLoggedIn`、`loadLabels`、`tcName`、`FORCE`。

## 验证

```bash
pnpm exec playwright test e2e/tests/version-policy.spec.ts --list
pnpm exec tsc -p e2e/tsconfig.json --noEmit
pnpm run typecheck
pnpm run lint
bash scripts/dev/static-scan-topn.sh 5
```

- Playwright `--list` 可发现 3 条用例；该结果只证明可发现，不作为验收通过证据。
- E2E TypeScript 检查已通过。
- 静态扫描报告：`.ai/reports/static-scan/20260522-005336/summary.md`

## 限制

- `http://127.0.0.1:5173` 和 `http://127.0.0.1:8080/actuator/health` 当前不可达，因此未执行浏览器实跑。

## 结论

- SA-007 scoped policy 真实页面候选旅程已有 Playwright 自动化用例；环境就绪后必须实跑真实页面才可计入验收。
