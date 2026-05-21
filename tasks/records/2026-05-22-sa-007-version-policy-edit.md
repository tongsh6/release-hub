# SA-007 版本策略编辑闭环

## 目标

- 补齐 SA-007 scoped policy 的编辑入口和后端更新能力。
- 让管理员可以修改版本策略名称、方案、递增规则和 GLOBAL / PROJECT / SUB_PROJECT 作用域。

## 变更

- `VersionPolicy` 新增 `update` 方法，保留 id、createdAt，并推进 updatedAt/version。
- `VersionPolicyController` 新增 `PUT /api/v1/version-policies/{id}`。
- `VersionPolicyE2ETest` 覆盖 scoped policy 从 PROJECT 更新为 SUB_PROJECT 后，applicable 查询按新作用域返回。
- `versionPolicyApi` 新增 `update`。
- `VersionPolicyList.vue` 复用创建弹窗提供编辑预填和保存。
- `VersionPolicyList.spec.ts` 增加编辑预填和 update payload 覆盖。

## 验证

```bash
mvn -q -pl releasehub-bootstrap -am -Dtest=VersionPolicyE2ETest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -DskipTests compile
pnpm exec vitest run src/views/version-policy/__tests__/VersionPolicyList.spec.ts
pnpm run typecheck
pnpm run lint
bash scripts/dev/static-scan-topn.sh 5
```

- 静态扫描报告：`.ai/reports/static-scan/20260522-004441/summary.md`

## 结论

- SA-007 scoped policy 基础管理已具备创建、编辑、删除闭环；后续剩余重点是外部 Playwright 真实页面场景验收和版本更新入口按组织/仓库范围选取默认策略。
