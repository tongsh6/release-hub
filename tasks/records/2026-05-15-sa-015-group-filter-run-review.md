# 2026-05-15 SA-015 Group Filter Run Review

## 任务

按项目台账当前 P1 队列，补 SA-015「分组筛选下的复核旅程」。

## 变更

- 后端 `GET /api/v1/runs/paged` 新增可选 `groupCode` 参数，通过 RunItem `windowKey` 关联发布窗口分组过滤 Run。
- 前端 Run 列表新增分组树筛选，复用 `GroupTreeSelect`，并把 `groupCode` 透传给 Run 分页 API。
- Playwright Slice-2 在 UI 创建出的分组下生成失败版本更新 Run 后，按 `windowKey` + 分组 + `FAILED` 过滤并复核 Run 抽屉证据。
- `scenario-acceptance-matrix.md` 和 `project-ledger.md` 已同步 SA-015 最新状态。
- 顺手清理本轮 TopN 静态扫描暴露的 SpotBugs 问题：领域对象构造期抛错、可变集合暴露、接口/基础设施可空响应体路径、格式化换行和未使用私有方法。

## 验证

- `mvn -pl releasehub-domain test`：通过，51 tests。
- `mvn -pl releasehub-application test`：通过，122 tests，5 skipped。
- `mvn -pl releasehub-bootstrap -am -Dtest=RunPagedApiTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，`RunPagedApiTest` 9 tests。
- `pnpm exec playwright test e2e/tests/slice-2-full-flow.spec.ts --grep "UI-created release orchestration journey"`：通过，5 tests。
- `pnpm run typecheck`：通过。
- `pnpm i18n:lint`：通过。
- `pnpm -s lint`：通过；仍有 4 个既有 warning，未引入新 warning。
- `mvn -pl releasehub-infrastructure test`：通过，52 tests。
- `mvn -pl releasehub-interfaces test`：通过，无测试源，编译通过。
- `scripts/dev/static-scan-topn.sh 10`：通过，复扫报告 `.ai/reports/static-scan/20260515-123640/summary.md`；SpotBugs 0，frontend lint/typecheck 通过。

## 后续

- SA-015 仍保留冲突详情和部分失败复核旅程。
- 需要继续补真实冲突类型详情和部分失败复核路径。
