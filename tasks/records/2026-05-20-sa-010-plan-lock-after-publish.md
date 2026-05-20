# Slice: SA-010 发布后发布计划变更锁定

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` SA-010 发布经理挂载迭代到发布窗口
- **日期**：2026-05-20
- **执行者**：AI
- **状态**：已完成

## 选择理由

- 场景矩阵将“更完整发布计划限制”列为 SA-010 剩余 P1。
- 列表页只在 DRAFT 窗口显示挂载入口，但详情页和后端此前仍允许 PUBLISHED 窗口 attach/detach，发布后计划可能被继续修改。
- 发布计划一旦进入 PUBLISHED，应作为编排和外部 GitLab 分支证据的稳定输入，避免后续 attach/detach 污染 Run、WindowIteration 和 release 分支审计。

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `backend/releasehub-application/src/main/java/io/releasehub/application/window/AttachAppService.java` | 修改 | Application |
| `backend/releasehub-application/src/test/java/io/releasehub/application/window/AttachAppServiceTest.java` | 修改 | Test |
| `frontend/src/views/release-window/ReleaseWindowDetail.vue` | 修改 | Frontend UI |
| `frontend/src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts` | 修改 | Frontend Test |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | Docs |
| `docs/project-ledger.md` | 修改 | Docs |
| `tasks/records/2026-05-20-sa-010-plan-lock-after-publish.md` | 新建 | Docs |

## 执行步骤

### Step 1: RED / 缺口确认

- `AttachAppService.ensureWindowCanChangeIterations` 只拒绝 CLOSED 和 frozen，PUBLISHED 会放行。
- `ReleaseWindowDetail.vue` 的 `canChangeIterations` 使用 `status !== 'CLOSED' && !frozen`，PUBLISHED 窗口详情仍显示挂载和解除挂载入口。
- `ReleaseWindowList.vue` 已只在 DRAFT 显示挂载入口，说明详情页与列表页存在行为不一致。

### Step 2: GREEN

- 后端收紧 `ensureWindowCanChangeIterations`：只有 DRAFT 且未冻结窗口允许 attach/detach。
- PUBLISHED/CLOSED 均复用现有 `RW_009` 无效状态错误，冻结窗口继续返回 `RW_006`。
- 前端详情页 `canChangeIterations` 收紧为 `form.status === 'DRAFT' && !form.frozen`。
- 组件测试新增发布后隐藏挂载和解除挂载控件断言，并保留 DRAFT 窗口解除挂载路径。

### Step 3: VERIFY

| 命令 | 结果 |
|------|------|
| `mvn -pl releasehub-application -am -Dtest=AttachAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS：9 passed |
| `pnpm exec vitest run src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts` | PASS：4 passed |

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | OK | 发布后窗口不能继续改变迭代集合 |
| 层级闭环 | OK | 后端强约束和前端入口隐藏同时收紧 |
| 测试闭环 | OK | 后端覆盖 PUBLISHED attach/detach 拒绝，前端覆盖发布后控件隐藏 |
| 架构闭环 | OK | 业务规则在 Application 服务入口执行，不依赖前端判断 |
| 文档闭环 | OK | 已同步场景矩阵、项目台账和本执行记录 |

## 经验沉淀

- [x] 不新增经验文档。本轮属于 SA-010 发布计划约束补强，规则已在台账和矩阵沉淀。
