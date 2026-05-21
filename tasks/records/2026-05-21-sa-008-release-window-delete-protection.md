# SA-008 发布窗口删除保护

日期：2026-05-21

## 用户旅行图

1. 发布经理在发布窗口列表查看 DRAFT 发布窗口。
2. 空 DRAFT 窗口可作为误建数据删除。
3. 已关联迭代的 DRAFT 窗口或已发布/已关闭窗口不能删除，避免破坏发布计划、Run 证据和分支证据。
4. 前端在后端拒绝删除时展示明确删除保护提示。

## 实现范围

- 新增 `RW_014 / error.rw.delete_blocked` 业务错误码和中英文后端文案。
- `ReleaseWindowAppService.delete` 仅允许空 DRAFT 窗口删除；非 DRAFT 或存在 `WindowIteration` 时拒绝。
- `ReleaseWindowPort` / JPA adapter 增加 `deleteById`，`ReleaseWindowController` 增加 `DELETE /api/v1/release-windows/{id}`。
- 前端 `releaseWindowApi.delete` 接入 DELETE API。
- 发布窗口列表对 DRAFT 窗口展示删除入口，并对 `RW_014` 展示 `releaseWindow.deleteBlocked`。
- `deleteProtectionMessageKey` 新增 `RW_014` 映射。
- 更新 SA-008 矩阵和场景详情，将删除保护纳入当前覆盖。

## 验证

- `mvn -q -pl releasehub-application -am -Dtest=ReleaseWindowAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - PASS
- `mvn -q -pl releasehub-bootstrap -am -Dtest=ReleaseWindowPageApiTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - PASS
- `pnpm exec vitest run src/views/release-window/__tests__/ReleaseWindowList.spec.ts src/utils/__tests__/deleteProtection.spec.ts`
  - 6 PASS / 0 FAIL
- `mvn -q -DskipTests compile`
  - PASS
- `pnpm run typecheck`
  - PASS
- `pnpm run lint`
  - PASS
- `bash scripts/dev/static-scan-topn.sh 5`
  - 报告：`.ai/reports/static-scan/20260522-000436/summary.md`
  - `git diff --check`、backend SpotBugs、frontend ESLint、frontend typecheck 均 PASS

## 非目标

- 不允许删除已发布或已关闭窗口。
- 不级联删除 `WindowIteration`、Run、RunItem、RunStep 或分支证据。
- 不启动完整前后端联调环境。
