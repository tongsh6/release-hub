# 2026-05-24 SA-005 仓库 Mock Provider 持久化边界

## 背景

产品运行库中的代码仓库记录应全部视为真实业务数据。`MOCK` Provider 只能用于隔离测试或适配器级测试，不能通过产品 UI/API 写入持久库。

## 范围

- 仓库创建/更新应用服务拒绝 `gitProvider=MOCK`，返回 `REPO_014`。
- 仓库表单不再展示 `MOCK` Provider 选项；历史 `MOCK` 仓库打开编辑时默认改为真实 Provider 选择。
- Slice-2 UI journey 不再用 `MOCK` Provider 落库，改为先准备真实 GitLab fixture，再通过 UI 创建 `GITLAB` 仓库。
- SA-002 safe-cleanup dry-run 新增 `MOCK_PROVIDER_IN_PERSISTENT_REPO` 风险，用于暴露历史持久库中的 Mock Provider 记录。
- repo-management spec、场景矩阵、项目台账和路线图同步 Mock 边界。

## 非目标

- 不删除历史 `MOCK` 仓库记录。
- 不直接改数据库。
- 不移除底层 `GitProvider.MOCK` 枚举或 Mock adapter；它们仍服务隔离测试。
- 不把当前任务扩展成批量历史数据迁移。

## 验证

```bash
mvn -f backend/pom.xml -pl releasehub-application -am -Dtest=CodeRepositoryAppServiceTest,DataQualityCleanupReviewAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f backend/pom.xml -pl releasehub-bootstrap -am -Dtest=RepositorySyncApiTest -Dsurefire.failIfNoSpecifiedTests=false test
bash -n scripts/acceptance/sa002-safe-cleanup.sh
cd frontend && pnpm run typecheck
cd frontend && pnpm i18n:lint
bash scripts/dev/check-roadmap.sh
git diff --check
bash scripts/dev/static-scan-topn.sh 10
```

## 结论

- 应用层仓库服务和 SA-002 复核契约测试：25 PASS / 0 FAIL / 0 SKIP。
- API 集成测试：4 PASS / 0 FAIL / 0 SKIP。
- `sa002-safe-cleanup.sh` 语法检查通过。
- 前端 typecheck 和 i18n lint 通过。
- Playwright Slice-2 用例加载检查通过：23 tests discoverable。
- 路线图检查通过：唯一 HEAD 指向 SA-002。
- `git diff --check` 通过。
- 静态扫描通过：`.ai/reports/static-scan/20260524-192807/summary.md`；SpotBugs 0，frontend lint PASS，frontend typecheck PASS。

Mock Provider 持久化边界已收口：产品 UI/API 不再允许 `MOCK` Provider 落库，历史 `MOCK` Provider 记录进入 SA-002 dry-run 风险复核。
