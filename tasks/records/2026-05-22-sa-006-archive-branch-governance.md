# SA-006 Archive 分支统计治理

## 目标

- 补强 SA-006 archive 规则缺口：`archive/...` 分支是系统归档行为产物，不应继续作为活跃分支或历史不合规分支风险展示。
- 保留总分支数可观察性，同时避免仓库健康统计把归档分支误判为命名规则违规。

## 变更

- `GitLabAdapter.fetchBranchStatistics` 统计时保留 `total` 为 GitLab 返回总数。
- `active` 排除 `archive/...` 分支。
- `nonCompliant` 只在 active 分支中计算，不再把归档分支计入风险。
- `GitLabAdapterTest` 增加 WireMock 用例覆盖 archived branch 不计 active/nonCompliant。

## 验证

```bash
mvn -q -pl releasehub-infrastructure -am -Dtest=GitLabAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl releasehub-application -am -Dtest=CodeRepositoryAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -DskipTests compile
pnpm run typecheck
pnpm run lint
bash scripts/dev/static-scan-topn.sh 5
```

- 静态扫描报告：`.ai/reports/static-scan/20260522-003105/summary.md`

## 结论

- SA-006 archive 分支已经在仓库同步统计中被单独治理，避免历史归档分支污染活跃分支风险；后续剩余重点是历史不合规分支治理入口和真实 GitLab 端到端 scoped rule 证据。
