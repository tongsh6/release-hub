# Slice: SA-010 发布窗口详情解除挂载入口

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` 七、当前推进队列：SA-010/SA-011 发布计划与风险详情
- **日期**：2026-05-19
- **执行者**：AI
- **状态**：已完成

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | 属于 SA-010 “解除挂载与更完整发布计划限制”缺口 |
| 用户价值 | ✅ | 发布经理可在窗口详情直接解除已关联迭代 |
| 端到端路径 | ✅ | 复用既有后端 API/应用服务，补前端入口、Vitest、矩阵和台账 |
| 单一目标 | ✅ | 只处理窗口详情解除挂载入口，不扩展 GitLab 不可达/权限失败 |
| 可独立验证 | ✅ | 目标 Vitest、typecheck、i18n lint、静态扫描 |
| 可回滚 | ✅ | 影响集中在发布窗口详情页与对应测试/文档 |
| 依赖明确 | ✅ | 依赖既有 `releaseWindowApi.detach` 和 `AttachAppService.detach` |
| 风险收敛 | ✅ | 前端状态约束对齐后端非冻结、非关闭规则；真实 GitLab/UI E2E 留作后续 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `frontend/src/views/release-window/ReleaseWindowDetail.vue` | 修改 | Frontend |
| `frontend/src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts` | 修改 | Test |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | Docs |
| `docs/project-ledger.md` | 修改 | Docs |
| `tasks/records/2026-05-19-sa-010-detach-window-iteration.md` | 新建 | Docs |

## 执行步骤

### Step 1: RED — 写失败测试

- **测试文件**：`frontend/src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts`
- **预期结果**：解除挂载按钮尚不存在，测试失败。
- **实际结果**：`detaches an associated iteration from the detail page and refreshes the list` 失败，未找到 `common.remove` 按钮。
- **证据**：首次运行 `pnpm exec vitest run src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts` 为 1 failed / 2 passed。

### Step 2: GREEN — 写实现

- **实现文件**：`frontend/src/views/release-window/ReleaseWindowDetail.vue`
- **实现内容**：关联迭代标题行增加带 `Delete` 图标的解除按钮；确认后调用 `releaseWindowApi.detach` 并刷新关联迭代列表；关联/解除入口统一使用 `canChangeIterations` 状态约束。
- **测试结果**：目标 Vitest 3 PASS。

### Step 3: REFACTOR

- **优化项**：将 `canChangeIterations` 收紧为必须已有窗口 id、非 `CLOSED`、非冻结，避免详情加载前误显操作入口。
- **测试结果**：目标 Vitest、typecheck、i18n lint 均通过。

### Step 4: VERIFY

| 命令 | 结果 |
|------|------|
| `pnpm exec vitest run src/views/release-window/__tests__/ReleaseWindowDetail.spec.ts` | 3 PASS |
| `pnpm run typecheck` | PASS |
| `pnpm i18n:lint` | PASS |
| `mvn -pl releasehub-application -Dtest=AttachAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | 6 PASS |
| `git diff --check` | PASS |
| `bash scripts/dev/static-scan-topn.sh 10` | PASS |

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | ✅ | 用户可从窗口详情解除关联迭代并看到列表刷新 |
| 层级闭环 | ✅ | 前端入口接通既有 API；后端应用服务和端口已存在 |
| 测试闭环 | ✅ | RED/GREEN 证据已记录，新增 Vitest 覆盖关键交互 |
| 架构闭环 | ✅ | 未改变 DDD 分层；复用既有 API/应用服务，未在前端推导业务真相 |
| 性能闭环 | ✅ | 仅在用户确认后刷新关联列表，无新增批量轮询或远程 N+1 |
| 文档闭环 | ✅ | 已同步场景矩阵、项目台账和本执行记录 |
| 工作区闭环 | ✅ | `git status --short` 已检查，静态扫描报告已归档 |

## 静态扫描

- **扫描命令**：`bash scripts/dev/static-scan-topn.sh 10`
- **报告路径**：`.ai/reports/static-scan/20260519-001037/summary.md`
- **TopN 处理结论**：TopN 未发现代码问题；git diff check、backend SpotBugs、frontend ESLint、frontend typecheck 均通过。
- **未解决风险**：无代码扫描风险；解除挂载真实 GitLab/UI E2E 复核仍为 SA-010 后续任务。

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| 解除挂载真实 GitLab/UI E2E 复核 | 本切片只补前端入口和现有 API 接通；真实外部状态证据需要完整环境 | `docs/reports/scenario-acceptance-matrix.md` SA-010 缺口 |
| GitLab 不可达/权限失败类风险 | 属于 SA-011 外部系统异常路径 | `docs/reports/scenario-acceptance-matrix.md` 当前推进队列 |

## 经验沉淀

- [x] 不需要。沿用既有 `iteration-attach-detach.md`、静态扫描和前端测试分层经验，未产生新的可复用模式。
