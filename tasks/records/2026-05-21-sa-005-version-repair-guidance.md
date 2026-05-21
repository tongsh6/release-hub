# SA-005 版本解析失败修复引导

日期：2026-05-21

## 用户旅行图

1. 系统管理员进入仓库详情页，查看已纳管仓库的组织路径、默认分支和初始版本状态。
2. 当初始版本为空或来源为 `VERSION_UNRESOLVED` 时，页面展示可执行的重新解析版本动作。
3. 管理员点击重新解析版本，前端调用既有 `POST /api/v1/repositories/{id}/sync-version`。
4. 解析成功后，页面内版本号和来源立即更新；解析失败则走统一错误处理，保留可复核状态。

## 实现范围

- 前端 `repositoryApi` 增加 `syncInitialVersion` 封装。
- 仓库详情页根据版本状态展示“重新解析版本”动作。
- 重新解析成功后直接更新当前页面 `initialVersion`，不要求用户离开详情页或手动刷新。
- 增加中英文 i18n 文案和 RepositoryDetail Vitest 覆盖。

## 验证

- `pnpm exec vitest run src/views/repository/__tests__/RepositoryDetail.spec.ts src/views/repository/__tests__/RepositoryDrawer.spec.ts src/utils/__tests__/cloneUrl.spec.ts`
  - 5 PASS / 0 FAIL / 0 SKIP
- `pnpm run typecheck`
  - PASS
- `pnpm i18n:lint`
  - PASS

## 非目标

- 不新增后端解析能力；本轮复用既有 `sync-version` 接口。
- 不处理组织筛选、仓库删除保护或 GitLab 连通性诊断细分。
