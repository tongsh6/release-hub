# SA-006 分支创建链路接入作用域规则

## 目标

- 延续 scoped check 能力，把真实 feature/release 分支创建和冲突扫描链路接入仓库上下文。
- 让项目级/仓库级 BranchRule 不只停留在独立 check API，而能约束迭代加仓、发布窗口挂载和冲突检测。

## 变更

- `IterationAppService` 在 AUTO / NAMED feature 分支校验时传入 `repo.groupCode` 和 `repoId`。
- `AttachAppService` 在 release 分支创建路径传入 `repo.groupCode` 和 `repoId`。
- `ConflictDetectionAppService` 在 feature/release 分支不合规检测时传入 `repo.groupCode` 和 `repoId`。
- 应用层测试更新为断言分支创建路径确实调用 scoped compliance。

## 验证

```bash
mvn -q -pl releasehub-application -am -Dtest=IterationAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl releasehub-application -am -Dtest=AttachAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl releasehub-application -am -Dtest=ConflictDetectionAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -DskipTests compile
pnpm run typecheck
pnpm run lint
bash scripts/dev/static-scan-topn.sh 5
```

- 静态扫描报告：`.ai/reports/static-scan/20260522-002829/summary.md`

## 结论

- SA-006 规则作用域已进入核心分支创建/检测链路；后续剩余重点收敛到 archive 规则、历史不合规分支治理和真实 GitLab 端到端证据。
