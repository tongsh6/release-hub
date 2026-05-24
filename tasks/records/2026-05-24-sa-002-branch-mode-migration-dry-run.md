# 2026-05-24 SA-002 BranchCreationMode 最小 dry-run 实现

## 背景

BranchCreationMode 迁移服务 proposal 评审结论为 `APPROVE_DRY_RUN_ONLY`。当前切片只实现只读 dry-run，禁止执行 API、数据库写入、迁移审计表、前端执行入口和 GitLab 远端操作。

## 范围

- 新增 `BranchCreationModeMigrationDryRunAppService`，基于只读端口扫描历史 `iteration_repo.branch_creation_mode` 风险。
- 新增只读端口和 JPA adapter，仅读取 `iteration_repo`。
- 新增只读 dry-run API：`POST /api/v1/data-quality/branch-creation-mode-migrations/dry-run`。
- 输出四类候选：`SAFE_AUTO_DEFAULTABLE`、`NORMALIZE_LEGAL_VALUE`、`MANUAL_MAPPING_REQUIRED`、`NOT_MIGRATABLE_IN_THIS_SERVICE`。
- 返回结构化 JSON 结果和 Markdown 报告，且 `executionPermitted=false`。

## 非目标

- 不实现 `approve-plan`、`execute`、`verify`、`rollback-plan` API。
- 不新增迁移批次表或审计表。
- 不写数据库。
- 不触碰 GitLab。
- 不修改 `feature_branch`、版本字段、发布窗口、迭代仓库集合或数据质量 case。

## 验证

```bash
mvn -f backend/pom.xml -pl releasehub-application -am -Dtest=BranchCreationModeMigrationDryRunAppServiceTest,DataQualityCleanupReviewAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f backend/pom.xml -pl releasehub-bootstrap -am -Dtest=DataQualityCleanupApiTest -Dsurefire.failIfNoSpecifiedTests=false test
bash scripts/dev/check-roadmap.sh
git diff --check
cd frontend && pnpm run typecheck
cd frontend && pnpm i18n:lint
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- 应用层 dry-run 与复核服务定向测试：11 PASS / 0 FAIL / 0 SKIP。
- API 集成测试：2 PASS / 0 FAIL / 0 SKIP。
- 路线图检查通过：唯一 HEAD 指向 SA-002。
- `git diff --check`、前端 typecheck 和 i18n lint 均通过。
- 静态扫描通过：`.ai/reports/static-scan/20260524-162409/summary.md`；SpotBugs 0，frontend lint PASS，frontend typecheck PASS。

## 结论

BranchCreationMode 最小 dry-run 已可作为下一阶段执行计划评审输入。真实执行、数据库写入和回滚能力仍必须另走评审门禁。
