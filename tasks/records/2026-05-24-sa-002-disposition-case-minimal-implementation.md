# 2026-05-24 SA-002 数据质量受控处置 case 最小实现

## 背景

SA-002 已完成受控处置执行审计设计。当前切片把设计落成最小可用产品能力：从已接受的 dry-run 复核动作创建处置 case，记录动作快照、幂等键、状态推进、前后复核快照、失败原因和恢复说明。

## 范围

- 后端新增 `DataQualityDispositionCaseAppService`、端口、视图和命令对象。
- 基础设施新增 `data_quality_disposition_case` 持久化表、JPA entity、repository 和 adapter。
- API 新增：
  - `POST /api/v1/data-quality/disposition-cases`
  - `GET /api/v1/data-quality/disposition-cases`
  - `GET /api/v1/data-quality/disposition-cases/{id}`
  - `POST /api/v1/data-quality/disposition-cases/{id}/start`
  - `POST /api/v1/data-quality/disposition-cases/{id}/verify`
  - `POST /api/v1/data-quality/disposition-cases/{id}/fail`
  - `POST /api/v1/data-quality/disposition-cases/{id}/cancel`
- 前端数据质量复核队列新增处置 case 创建、列表、详情抽屉和状态记录入口。

## 安全边界

- 创建 case 只保存审计记录，不修改业务资源。
- `APPLICATION_MANUAL` 只允许从 `PLANNED` 进入 `IN_PROGRESS`，并要求执行前快照。
- `OBSERVE_ONLY` 可记录复核结论，但不能进入执行状态。
- `MIGRATION_REQUIRED` 不允许进入应用内执行状态，后续必须独立迁移服务 proposal。
- 所有接口不删除数据库记录、不关闭发布窗口、不迁移业务数据、不触碰 GitLab 远端资源。

## 验证

```bash
mvn -f backend/pom.xml -pl releasehub-application -am -Dtest=DataQualityDispositionCaseAppServiceTest,DataQualityCleanupReviewAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f backend/pom.xml -pl releasehub-bootstrap -am -Dtest=DataQualityDispositionCaseApiTest,DataQualityCleanupApiTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/data-quality/__tests__/DataQualityReviewQueue.spec.ts
pnpm run typecheck
pnpm i18n:lint
bash scripts/dev/check-roadmap.sh
git diff --check
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- 应用层处置 case 测试通过：6 PASS / 0 FAIL / 0 SKIP。
- 数据质量 API 测试通过：2 PASS / 0 FAIL / 0 SKIP。
- 前端数据质量复核队列通过：3 PASS / 0 FAIL。
- 前端 typecheck 通过。
- i18n lint 通过。
- roadmap 检查通过。
- `git diff --check` 通过。
- 静态扫描通过：`.ai/reports/static-scan/20260524-152044/summary.md`；SpotBugs 0，frontend lint PASS，frontend typecheck PASS。

## 结论

SA-002 已从“处置执行审计设计”推进到“处置 case 最小实现”。当前仍然只记录审计与人工处置状态，不执行自动清理。
