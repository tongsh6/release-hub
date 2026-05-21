# SA-005 仓库组织筛选

日期：2026-05-21

## 用户旅行图

1. 系统管理员进入仓库列表，准备复核某个组织范围下的已纳管仓库。
2. 管理员在筛选区选择一个组织节点，可以是叶子组织，也可以是上级组织。
3. 管理员点击查询，列表只展示该组织及其所有子组织下的仓库。
4. 管理员可继续打开仓库详情，复核组织路径、默认分支和初始版本状态。

## 实现范围

- 后端仓库分页查询增加 `groupCode` 筛选参数。
- 应用层先校验组织存在，再收集当前组织及子组织 code 集合，避免接口层或持久层泄漏组织树规则。
- 持久层增加按组织 code 集合和关键字组合分页查询。
- 前端仓库列表筛选区增加组织树选择器，并在查询时向列表 API 传递 `groupCode`。
- 增加应用层单测、Repository E2E 和 RepositoryList Vitest 覆盖。

## 验证

- `mvn -pl releasehub-domain,releasehub-application -am -Dtest=CodeRepositoryTest,CodeRepositoryAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 19 PASS / 0 FAIL / 0 SKIP
- `mvn -pl releasehub-bootstrap -am -Dtest=RepositoryE2ETest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 12 PASS / 0 FAIL / 0 SKIP
- `pnpm exec vitest run src/views/repository/__tests__/RepositoryList.spec.ts src/views/repository/__tests__/RepositoryDetail.spec.ts src/views/repository/__tests__/RepositoryDrawer.spec.ts src/utils/__tests__/cloneUrl.spec.ts`
  - 6 PASS / 0 FAIL / 0 SKIP
- `pnpm run typecheck`
  - PASS
- `pnpm i18n:lint`
  - PASS

## 非目标

- 不处理仓库删除保护。
- 不改变仓库必须挂叶子分组的创建/更新约束。
- 不新增跨组织权限模型。
