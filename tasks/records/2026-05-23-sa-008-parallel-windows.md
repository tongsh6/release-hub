# SA-008 多窗口并行发布可观测性

## 背景

- SA-008 已覆盖发布窗口创建、列表/日历、组织筛选、冻结限制和删除保护。
- 风险池仍保留“多窗口并行发布”样本：同一组织多个活跃发布窗口并存时，用户需要确认列表、日历、详情和发布计划不会把不同窗口的迭代、仓库和计划项混在一起。

## 改动

- 后端新增 `GET /api/v1/release-windows/{id}/parallel-scope` 读模型，返回当前窗口、同组活跃窗口、迭代数、仓库数和发布计划项。
- 发布窗口列表 DTO 增加 `parallelActiveWindowCount` 与 `parallelActiveWindowKeys`，列表和日历可展示同组并行窗口线索。
- 发布窗口详情页新增同组并行窗口表，展示当前窗口、组织编码、活跃窗口数、迭代数、仓库数和发布计划项。
- OpenSpec、场景矩阵、项目台账和路线图已同步；SA-008 出队，下一 HEAD 转向 SA-009 大规模迭代仓库可观测性。

## 验证

```bash
mvn -pl releasehub-bootstrap -am -Dtest=ReleaseWindowPageApiTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/release-window/__tests__/ReleaseWindowList.spec.ts src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts
pnpm run typecheck
pnpm i18n:lint
git diff --check
bash scripts/dev/check-roadmap.sh
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- `ReleaseWindowPageApiTest`：5 PASS / 0 FAIL / 0 SKIP。
- `ReleaseWindowList.spec.ts` 与 `ReleaseWindowDetail.spec.ts`：14 PASS / 0 FAIL / 0 SKIP。
- `typecheck`、`i18n:lint`、`git diff --check`、路线图检查均通过；路线图唯一 HEAD 指向 SA-009。
- 最终静态扫描通过，SpotBugs 0 bugs；报告：`.ai/reports/static-scan/20260523-142409/summary.md`。

## 非目标

- 不做跨窗口自动排期优化。
- 不改变窗口创建、发布、关闭、冻结、删除保护和组织筛选语义。
- 不改变真实 GitLab 分支创建、发布编排、关闭窗口、CI 触发状态和 retry 语义。
