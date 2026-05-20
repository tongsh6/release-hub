# Slice: SA-015 Run 详情失败项重试前端闭环

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` SA-015 测试人员复核发布状态和执行证据
- **日期**：2026-05-20
- **执行者**：AI
- **状态**：已完成

## 选择理由

- 后端和真实 GitLab 验收已证明 retry API 会只重试选中的失败项，但 Run 详情页只展示部分失败证据，不能直接从详情页发起失败项重试。
- Run 列表已有批量 retry 逻辑，详情页缺少同等闭环，测试人员需要在看到失败项后回到列表操作。
- 本切片只补前端可操作性：复用现有 `/runs/{id}/retry` API，不改后端执行语义。

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `frontend/src/views/run/RunDetail.vue` | 修改 | Frontend UI |
| `frontend/src/views/run/__tests__/RunDetail.spec.ts` | 新建 | Frontend Test |
| `frontend/src/i18n/messages/en-US.ts` | 修改 | Frontend i18n |
| `frontend/src/i18n/messages/zh-CN.ts` | 修改 | Frontend i18n |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | Docs |
| `docs/project-ledger.md` | 修改 | Docs |
| `tasks/records/2026-05-20-sa-015-run-detail-retry.md` | 新建 | Docs |

## 执行步骤

### Step 1: RED / 缺口确认

- `RunList.vue` 可读取 Run 详情、筛选 `FAILED` / `MERGE_BLOCKED` item，并调用 `runApi.retry`。
- `RunDetail.vue` 只支持导出、任务刷新和旧 task retry，未对 RunItem 部分失败提供 retry 入口。

### Step 2: GREEN

- Run 详情页新增“重试失败项”按钮，仅当当前 Run 存在 `FAILED` 或 `MERGE_BLOCKED` item 时显示。
- 点击后确认、校验 `run:write` 权限和当前用户，按 `windowKey::repoId::iterationKey` 只提交失败项。
- retry 成功后切换到新 Run 详情，并刷新 Run 详情与任务列表。
- 新增组件测试覆盖：
  - 同一 Run 内成功项和 `MERGE_BLOCKED` 项并存时，只提交失败项。
  - 无失败项时不显示重试入口。

### Step 3: VERIFY

| 命令 | 结果 |
|------|------|
| `pnpm exec vitest run src/views/run/__tests__/RunDetail.spec.ts` | PASS：2 passed |
| `pnpm run typecheck` | PASS |
| `pnpm i18n:lint` | PASS |

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | OK | 测试人员可在 Run 详情看到部分失败后直接重试失败项 |
| 层级闭环 | OK | 前端复用既有 retry API，后端强证据仍由真实 GitLab 验收承担 |
| 测试闭环 | OK | Vitest 覆盖失败项筛选、请求体和无失败项隐藏入口 |
| 文档闭环 | OK | 已同步场景矩阵、项目台账和本执行记录 |

## 经验沉淀

- [x] 不新增经验文档。本轮为既有 retry 能力的前端闭环补齐。
