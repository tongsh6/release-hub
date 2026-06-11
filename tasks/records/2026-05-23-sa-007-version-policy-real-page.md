# SA-007 版本策略真实页面验收

## 目标

- 把 SA-007 scoped policy 管理从“候选 Playwright 可发现”推进到“真实前端、真实后端页面验收通过”。
- 覆盖管理员在版本策略页创建、编辑、删除 GLOBAL / PROJECT / SUB_PROJECT 策略，以及项目级必填校验和列表 scope 明细复核。

## 变更

- 扩展 `frontend/e2e/tests/version-policy.spec.ts`：
  - GLOBAL 策略：创建、编辑名称、列表复核全局作用域、删除清理。
  - PROJECT 策略：验证缺失项目 ID 时页面阻止保存，补齐项目 ID 后创建、编辑名称、列表复核项目 ID、删除清理。
  - SUB_PROJECT 策略：创建、编辑名称、列表复核项目 ID 与子项目 ID、删除清理。
- 修正 Playwright 定位：
  - 作用域单选使用精确文本，避免“项目”误匹配“子项目”。
  - 项目 ID 输入使用 `exact: true`，避免误匹配“子项目 ID”。
- 保留版本更新入口策略选择 route-stub 用例的定位：它是 UI 回归，不计入场景化验收通过。

## 验证

```bash
pnpm exec tsc -p e2e/tsconfig.json --noEmit
pnpm exec playwright test e2e/tests/version-policy.spec.ts
pnpm exec vitest run src/views/version-policy/__tests__/VersionPolicyList.spec.ts
mvn -q -pl releasehub-bootstrap -am -Dtest=VersionPolicyE2ETest -Dsurefire.failIfNoSpecifiedTests=false test
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- E2E TypeScript 检查通过。
- 外部 Playwright 真实页面验收：`3 passed`。
- 版本策略页 Vitest：`6 passed`。
- 后端 `VersionPolicyE2ETest` 通过，确认 scoped policy 创建、更新后 applicable 查询仍按最具体作用域返回。
- Top10 静态扫描通过，报告：`.ai/reports/static-scan/20260523-000924/summary.md`。

## 结论

- SA-007 P0 可出队。
- 下一步按路线图转向 SA-009：移除迭代仓库后的 feature 分支归档真实 GitLab 证据。
