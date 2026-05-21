# SA-009 迭代删除保护前端提示

日期：2026-05-21

## 用户旅行图

1. 技术负责人在迭代列表尝试删除一个仍关联仓库或已挂载发布窗口的迭代。
2. 后端以 `ITER_002` 拒绝删除，保护已有发布计划和迭代仓库范围。
3. 前端列表展示明确的删除保护提示，不把业务阻断当作未知错误处理。

## 实现范围

- `deleteProtectionMessageKey` 新增 `ITER_002 -> iteration.deleteBlocked` 映射。
- 迭代列表删除失败时复用删除保护映射，命中业务保护错误后展示 `ElMessage.warning` 并跳过通用错误处理。
- 中英文文案新增迭代删除保护提示。
- `IterationList.spec.ts` 覆盖 `ITER_002` 提示路径和未知错误回退路径。
- `deleteProtection.spec.ts` 覆盖迭代错误码映射。
- 更新 SA-009 矩阵和场景详情，将迭代删除保护前端提示纳入当前覆盖。

## 验证

- `pnpm exec vitest run src/views/iteration/__tests__/IterationList.spec.ts src/views/iteration/__tests__/IterationDetail.spec.ts src/utils/__tests__/deleteProtection.spec.ts`
  - 5 PASS / 0 FAIL
- `pnpm run typecheck`
  - PASS
- `pnpm run lint`
  - PASS
- `bash scripts/dev/static-scan-topn.sh 5`
  - 报告：`.ai/reports/static-scan/20260521-234947/summary.md`
  - `git diff --check`、backend SpotBugs、frontend ESLint、frontend typecheck 均 PASS

## 非目标

- 不改变后端 `ITER_002` 删除保护语义。
- 不补移除仓库后的真实 GitLab feature 分支归档证据。
- 不启动完整前后端联调环境。
