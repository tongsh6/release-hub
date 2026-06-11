# 2026-05-23 SA-001 发布候选评审页 / 发布经理检查清单

## 背景

发布候选报告已经形成，但人工评审入口仍分散在报告、场景矩阵、项目台账和任务记录中。发布经理需要一个应用内入口，能看到发布候选结论、证据索引、数据质量风险、暂缓项，并记录本轮签核结论。

## 范围

- 新增发布候选评审摘要 API 和页面入口。
- 聚合候选结论、验收证据、静态扫描状态、数据质量风险、残留非目标和发布经理检查清单。
- 支持发布经理记录签核结论、备注和检查项状态。
- 签核只记录评审结论，不改变发布窗口、仓库、GitLab、数据质量清理或发布编排状态。
- 同步更新 release-governance OpenSpec、场景矩阵、项目台账和执行路线图。

## 实现

- 后端新增 `ReleaseGovernanceAppService`，提供候选摘要和签核命令校验。
- 新增 `ReleaseCandidateSignoffPort` 及 JPA 持久化适配器，使用 `release_candidate_signoff` 表保存签核记录。
- 新增 `GET /api/v1/release-governance/candidate-review` 和 `POST /api/v1/release-governance/candidate-review/signoffs`。
- 前端新增 `frontend/src/views/release-governance/ReleaseCandidateReview.vue`、`releaseGovernanceApi` 和侧边栏路由入口。
- 页面展示证据、风险、检查清单、签核表单和最近签核。

## 验证

```bash
mvn -f backend/pom.xml -pl releasehub-application -am -Dtest=ReleaseGovernanceAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f backend/pom.xml -pl releasehub-bootstrap -am -Dtest=ReleaseGovernanceApiTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/release-governance/__tests__/ReleaseCandidateReview.spec.ts
pnpm run typecheck
pnpm i18n:lint
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- 应用层发布治理服务：4 PASS / 0 FAIL / 0 SKIP。
- API 发布候选评审入口：1 PASS / 0 FAIL / 0 SKIP；Flyway V32 迁移已在测试启动时应用。
- 前端发布候选评审页：2 PASS / 0 FAIL。
- 前端 typecheck 通过。
- i18n lint 通过。
- 静态扫描通过：报告 `.ai/reports/static-scan/20260523-202310/summary.md`；SpotBugs 0，typecheck PASS，frontend lint PASS。

## 浏览器冒烟

- 启动 Vite dev server 后打开 `/release-governance/candidate-review`。
- 通过 Playwright 预置登录态、`/v1/me` 和发布治理 API 响应。
- 页面标题为“发布候选评审 - ReleaseHub (Local)”。
- 页面展示候选结论、验收证据、风险与边界、发布经理检查清单和人工签核表单。
- 填写备注并点击“记录签核”后，页面展示最近签核：`release-manager / APPROVE_FOR_CONTROLLED_REVIEW / 2026-05-23T12:00:00Z`。

## 结论

SA-001 发布候选评审入口已页面化。发布经理现在可以在应用内复核候选结论和证据，并记录人工签核；该签核不触发发布、清理、GitLab 或数据状态变更。
