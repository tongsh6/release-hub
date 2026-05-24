# 2026-05-24 SA-002 数据质量人工复核处置策略

## 背景

SA-002 已具备 dry-run 报告、人工复核 API、应用内复核队列、验收数据命名空间、保留策略和数据源口径边界。但发布前人工评审仍需要把历史风险从“可见、可复核”推进到“知道能不能处置、在哪里处置、失败如何回滚、审计记录什么”。

本切片只形成受控处置策略和页面可见字段，不执行真实清理；继续禁止脚本自动删库、自动关闭发布窗口、自动迁移业务数据或触碰 GitLab 远端资源。

## 范围

- 在复核契约中为每条动作返回：
  - `dispositionLevel`：处置等级。
  - `allowedAction`：允许的业务动作。
  - `rollbackBoundary`：失败回滚边界。
  - `auditRecord`：审计记录口径。
- 将风险类型分为：
  - `OBSERVE_ONLY`：只读观察。
  - `APPLICATION_MANUAL`：应用层人工处置。
  - `MIGRATION_REQUIRED`：需要独立迁移服务设计。
- 数据质量复核队列展示上述策略字段，操作者在页面内即可区分可处置、不可直接处置和需要迁移设计的风险。
- 所有结果继续保持 `executionPermitted=false`。

## 实现

- `DataQualityCleanupReviewAppService` 的受控动作契约补充处置等级、允许动作、回滚边界和审计记录口径。
- `CleanupActionReview` 扩展复核结果字段，API 响应保留策略信息。
- 风险处置策略：
  - 仓库 token 明文、系统设置 token 明文、featureBranch 缺失、cloneUrl 异常和 DRAFT 发布窗口残留：进入 `APPLICATION_MANUAL`，必须通过现有应用入口和服务不变量处理。
  - BranchCreationMode 缺失或非法：进入 `MIGRATION_REQUIRED`，必须另建受控迁移服务，不能由复核队列直接执行。
  - attach 分支未创建：进入 `OBSERVE_ONLY`，保持只读复核和发布计划上下文确认。
- `DataQualityReviewQueue.vue` 展示处置等级、允许动作、回滚边界和审计记录。
- OpenSpec、场景矩阵、项目台账、路线图和发布候选报告同步记录该策略。

## 验证

```bash
mvn -f backend/pom.xml -pl releasehub-application -am -Dtest=DataQualityCleanupReviewAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f backend/pom.xml -pl releasehub-bootstrap -am -Dtest=DataQualityCleanupApiTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/data-quality/__tests__/DataQualityReviewQueue.spec.ts
pnpm run typecheck
pnpm i18n:lint
bash scripts/dev/check-roadmap.sh
git diff --check
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- 应用层复核服务：9 PASS / 0 FAIL / 0 SKIP；验证应用层人工处置、迁移服务要求和只读观察三类策略。
- API 复核入口：1 PASS / 0 FAIL / 0 SKIP；响应返回处置等级、允许动作、回滚边界、审计记录且 `executionPermitted=false`。
- 前端复核队列组件：2 PASS / 0 FAIL；页面展示策略字段。
- 前端 typecheck 通过。
- i18n lint 通过。
- roadmap 检查通过：HEAD 唯一且转向 SA-002 数据质量受控处置执行审计设计。
- `git diff --check` 通过。
- 静态扫描通过：报告 `.ai/reports/static-scan/20260524-145408/summary.md`。

## 结论

SA-002 已从“复核队列可见”推进到“每条风险具备处置等级、允许动作、回滚边界和审计记录口径”。当前仍不执行清理；下一步若要真正处置历史风险，必须先设计应用层执行审计闭环，明确动作来源、执行记录、失败恢复和回放防重。
