# SA-009 分支模式落库与迭代详情可观察性

日期：2026-05-21

## 用户旅行图

1. 技术负责人在迭代中通过 AUTO、NAMED 或 EXISTING 模式关联仓库。
2. 系统把该仓库的分支创建模式与 feature 分支、版本信息一并写入迭代仓库记录。
3. 技术负责人回到迭代详情，能在关联仓库表中复核每个仓库的分支模式、feature 分支、基础版本、开发版本、目标版本、版本来源和同步时间。

## 实现范围

- `IterationRepoPort.saveWithVersion` 增加 `BranchCreationMode` 参数。
- `IterationAppService.setupRepoForIteration` 保存实际使用的分支创建模式。
- `IterationRepoJpaEntity` / `IterationRepoPersistenceAdapter` 写入并读取 `branch_creation_mode`，空值默认按 `AUTO` 返回，兼容旧数据。
- 迭代详情关联仓库表新增分支创建模式、版本来源和同步时间展示，并用 `repoRows` 把仓库资料与版本信息合并供页面复核。
- `IterationAppServiceTest` 校验 AUTO / NAMED / EXISTING 模式保存参数。
- 新增 `IterationRepoPersistenceAdapterTest` 覆盖分支模式持久化和读取映射。
- `IterationDetail.spec.ts` 覆盖详情页可拿到分支模式和版本元数据。
- 更新 SA-009 矩阵和场景详情，移除“前端迭代详情可观察性”缺口。

## 验证

- `mvn -q -pl releasehub-application -am -Dtest=IterationAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - PASS
- `mvn -q -pl releasehub-infrastructure -am -Dtest=IterationRepoPersistenceAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - PASS
- `pnpm exec vitest run src/views/iteration/__tests__/IterationDetail.spec.ts src/views/iteration/__tests__/IterationList.spec.ts src/utils/__tests__/deleteProtection.spec.ts`
  - 6 PASS / 0 FAIL
- `mvn -q -DskipTests compile`
  - PASS
- `pnpm run typecheck`
  - PASS
- `pnpm run lint`
  - PASS
- `bash scripts/dev/static-scan-topn.sh 5`
  - 报告：`.ai/reports/static-scan/20260521-235717/summary.md`
  - `git diff --check`、backend SpotBugs、frontend ESLint、frontend typecheck 均 PASS

## 非目标

- 不改变 AUTO / NAMED / EXISTING 分支创建规则。
- 不补移除仓库后的真实 GitLab feature 分支归档证据。
- 不启动完整前后端联调环境。
