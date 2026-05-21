# SA-007 版本策略作用域与继承

## 目标

- 补强 SA-007 “分组/仓库作用域和策略继承”缺口。
- 让版本策略具备 GLOBAL / PROJECT / SUB_PROJECT 作用域元数据，并提供按当前范围查询可继承策略的 API。

## 变更

- 新增 `VersionPolicyScope` 值对象，支持作用域匹配和 specificity 计算。
- `VersionPolicy` 聚合新增 scope 字段，既有内置策略默认 `GLOBAL`。
- 新增 Flyway 迁移 `V31__add_scope_to_version_policy.sql`，为 `version_policy` 增加 scope columns 和索引。
- JPA 实体、Persistence Adapter、API View 均返回 scope。
- `VersionPolicyController` 新增：
  - `POST /api/v1/version-policies` 创建 scoped policy。
  - `DELETE /api/v1/version-policies/{id}` 删除 policy。
  - `GET /api/v1/version-policies/applicable?scopeProjectId=&scopeSubProjectId=` 按 `SUB_PROJECT > PROJECT > GLOBAL` 返回可继承策略。
- 前端 `versionPolicyApi` 类型和 `applicable` API 已补齐。

## 验证

```bash
mvn -q -pl releasehub-bootstrap -am -Dtest=VersionPolicyE2ETest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl releasehub-application -am -Dtest=VersionValidationAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -DskipTests compile
pnpm run typecheck
pnpm run lint
bash scripts/dev/static-scan-topn.sh 5
```

- 静态扫描报告：`.ai/reports/static-scan/20260522-003540/summary.md`

## 结论

- SA-007 已具备版本策略作用域元数据、PostgreSQL 持久化迁移和可继承策略查询 API；后续可继续补前端创建/编辑完整旅程与版本更新入口按组织/仓库范围选取默认策略。
