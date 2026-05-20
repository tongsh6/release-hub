# Slice: SA-014 批量版本更新前端入口

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` SA-014 技术负责人执行版本更新
- **日期**：2026-05-20
- **执行者**：AI
- **状态**：已完成

## 选择理由

- 后端已经提供 `/release-windows/{id}/execute/batch-version-update`，但发布窗口版本更新弹窗只提交单仓请求。
- 场景矩阵将 SA-014 的多仓版本更新和多仓部分失败列为 P2 扩展。
- 本切片先补前端入口和 API 契约，不改变后端版本更新器或真实 GitLab 验收脚本。

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `frontend/src/api/modules/releaseWindow.ts` | 修改 | Frontend API |
| `frontend/src/views/release-window/VersionUpdateDialog.vue` | 修改 | Frontend UI |
| `frontend/src/views/release-window/__tests__/VersionUpdateDialog.spec.ts` | 修改 | Frontend Test |
| `frontend/src/i18n/messages/en-US.ts` | 修改 | Frontend i18n |
| `frontend/src/i18n/messages/zh-CN.ts` | 修改 | Frontend i18n |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | Docs |
| `docs/project-ledger.md` | 修改 | Docs |

## 执行步骤

### Step 1: RED / 缺口确认

- `VersionUpdateController` 已有批量版本更新端点。
- `releaseWindowApi` 没有对应前端方法。
- `VersionUpdateDialog` 只支持单仓 `executeVersionUpdate`。

### Step 2: GREEN

- 前端 API 新增 `executeBatchVersionUpdate` 和请求类型。
- 多仓发布窗口打开版本更新弹窗时，可在单仓和已选仓库批量范围间切换。
- 批量模式默认选中当前窗口关联仓库，按每个仓库的 cloneUrl 派生 repoPath，复用统一 buildTool、targetVersion 和 POM/Gradle 路径。
- 修复切换批量模式时已选多仓被收窄为单仓的问题。

### Step 3: VERIFY

| 命令 | 结果 |
|------|------|
| `pnpm exec vitest run src/views/release-window/__tests__/VersionUpdateDialog.spec.ts` | PASS：2 passed |
| `pnpm run typecheck` | PASS |
| `pnpm i18n:lint` | PASS |

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | OK | 多仓窗口可从版本更新弹窗提交批量版本更新请求 |
| 层级闭环 | OK | 前端接入既有后端批量端点，不新增后端语义 |
| 测试闭环 | OK | Vitest 覆盖当前窗口仓库作用域和批量请求体 |
| 文档闭环 | OK | 已同步场景矩阵、项目台账和本执行记录 |

## 经验沉淀

- [x] 不新增经验文档。本轮为既有批量端点的前端入口补齐。
