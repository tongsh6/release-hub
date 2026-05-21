# SA-008 发布窗口组织路径与组织筛选

日期：2026-05-21

## 用户旅行图

1. 发布经理进入发布窗口列表。
2. 发布经理可以按组织节点筛选发布窗口；选择上级组织时，列表包含该组织及其子组织下的窗口。
3. 发布经理在列表中直接看到发布窗口归属的组织路径，用于复核窗口范围。
4. 发布经理仍可按名称和状态组合筛选。

## 实现范围

- 后端 `GET /api/v1/release-windows/paged` 增加 `groupCode` 查询参数。
- 应用层校验组织存在，并递归收集当前组织及子组织 code 后传给持久层查询。
- 持久层分页查询增加 `groupCode IN (...)` 条件，保留名称和状态筛选。
- 前端发布窗口列表增加组织树筛选，列表增加组织路径列。
- 新增 ReleaseWindowList Vitest，覆盖组织筛选参数和组织路径解析。
- 扩展 ReleaseWindowPageApiTest，覆盖按上级组织筛选时包含子组织窗口且排除其他组织窗口。

## 验证

- `mvn -q -pl releasehub-application -am -Dtest=ReleaseWindowAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - PASS
- `mvn -q -pl releasehub-bootstrap -am -Dtest=ReleaseWindowPageApiTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - PASS
- `mvn -q -DskipTests compile`
  - PASS
- `pnpm exec vitest run src/views/release-window/__tests__/ReleaseWindowList.spec.ts src/views/release-window/__tests__/ConflictPanel.spec.ts src/views/branch-rule/__tests__/BranchRuleList.spec.ts`
  - 8 PASS / 0 FAIL
- `pnpm run typecheck`
  - PASS
- `pnpm run i18n:lint`
  - PASS
- `pnpm run lint`
  - PASS
- `bash scripts/dev/static-scan-topn.sh 5`
  - 报告：`.ai/reports/static-scan/20260521-234132/summary.md`
  - `git diff --check`、backend SpotBugs、frontend ESLint、frontend typecheck 均 PASS

## 非目标

- 不补发布日历组织筛选。
- 不补冻结后按钮隐藏/禁止编辑的前端可观察性证据；该项仍留在 SA-008 后续切片。
- 不改变发布窗口创建时必须选择叶子组织的既有约束。
