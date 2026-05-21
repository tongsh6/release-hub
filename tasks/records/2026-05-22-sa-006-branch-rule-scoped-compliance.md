# SA-006 分支规则作用域合规解析

## 目标

- 补强 SA-006：BranchRule 已能配置 GLOBAL / PROJECT / SUB_PROJECT 作用域，但合规检查此前没有按作用域解析，项目级规则无法覆盖全局规则。
- 提供后端 check API 的作用域参数，为后续分支创建链路接入真实项目/仓库上下文打基础。

## 变更

- `BranchRuleScope` 新增作用域匹配和 specificity 计算。
- `BranchRuleAppService.isCompliant(branchName, projectId, subProjectId)` 按 `SUB_PROJECT > PROJECT > GLOBAL` 选择最具体规则集；存在更具体规则时不回退到全局规则。
- `GET /api/v1/branch-rules/check` 新增可选 `scopeProjectId`、`scopeSubProjectId` 参数。
- 前端 `branchRuleApi.check` 支持传入作用域参数。
- `BranchRuleAppServiceTest` 覆盖项目级覆盖全局、子项目级覆盖项目级。
- `BranchRuleE2ETest` 覆盖 scoped check API 行为。

## 验证

```bash
mvn -q -pl releasehub-application -am -Dtest=BranchRuleAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl releasehub-bootstrap -am -Dtest=BranchRuleE2ETest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -DskipTests compile
pnpm run typecheck
pnpm run lint
bash scripts/dev/static-scan-topn.sh 5
```

- 静态扫描报告：`.ai/reports/static-scan/20260522-002421/summary.md`

## 结论

- SA-006 的规则作用域已从“可配置/可展示”推进到后端合规解析与 API 契约可验证；后续剩余重点是把分支创建链路传入真实项目/仓库上下文，以及 archive 规则和历史不合规治理。
