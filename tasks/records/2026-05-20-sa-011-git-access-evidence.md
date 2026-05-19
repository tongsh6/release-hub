# Slice: SA-011 Git 访问异常真实证据与前端旅程

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` SA-011 风险详情 P1 缺口
- **日期**：2026-05-20
- **执行者**：AI
- **状态**：已完成

## 选择理由

台账 Top Priority 明确指出 SA-011 剩余缺口是 Git 访问异常的真实 GitLab/Playwright 证据。上轮已经完成 `GIT_PERMISSION_DENIED` / `GIT_UNAVAILABLE` 的领域建模、应用分型、Adapter 语义和前端组件展示，但证据仍停留在单测与组件层。

本切片选择补证据而不是开新功能，原因是它直接服务 v0.1.11 “场景矩阵驱动收口”：测试人员必须能从窗口详情看到权限不足/平台不可达这类外部阻断，并且后端验收必须证明真实 GitLab 401/不可达不会被吞掉、冒泡为扫描失败，或误判为普通合并冲突。

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | 通过 | 属于 SA-011 风险详情证据缺口 |
| 用户价值 | 通过 | 用户可区分业务冲突与 Git 外部访问故障 |
| 端到端路径 | 通过 | 后端验收脚本 + GitLab 探针 + 前端 Playwright |
| 单一目标 | 通过 | 只补 Git 访问异常证据，不扩展 RBAC/通知/CI |
| 可独立验证 | 通过 | `run-acceptance.sh`、Slice-2 Playwright、typecheck/i18n、静态扫描 |
| 可回滚 | 通过 | 改动集中在验收脚本、E2E 测试和文档 |
| 依赖明确 | 通过 | 依赖既有 Git 访问风险类型化实现 |
| 风险收敛 | 通过 | 探针仓库为验收数据，不破坏 seed repo；不可达使用 localhost 关闭端口 |

## 涉及文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `scripts/acceptance/run-acceptance.sh` | 修改 | 升级 v3.12，新增 5.9 Git 访问异常真实证据段 |
| `frontend/e2e/tests/slice-2-full-flow.spec.ts` | 修改 | 新增 SA-011 Git 访问异常窗口详情用户旅程 |
| `docs/project-ledger.md` | 修改 | 同步事实台账、验证结果和 Top Priority |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | 同步 SA-011 证据矩阵和最新验证记录 |
| `tasks/records/2026-05-20-sa-011-git-access-evidence.md` | 新增 | 本切片执行记录 |

## 执行摘要

### RED

- 先运行新增 Playwright 用例的单测过滤命令，确认 serial 套件依赖前置 UI-created 旅程，单独运行会因 `windowDetailUrl` 未建立失败。
- 该失败说明本项目 Slice-2 的用户旅程证据必须按完整 serial 流程执行，不能把后续窗口详情观察从 UI 创建业务数据的旅程中剥离。

### GREEN

- `run-acceptance.sh` 新增 5.9：
  - 创建本轮唯一发布窗口。
  - 注册权限不足探针仓库：复用真实 GitLab seed repo cloneUrl，但写入无效 token，触发真实 401/403 类路径。
  - 注册不可达探针仓库：使用 `http://localhost:65535/...` 触发连接不可达。
  - 创建迭代、attach，再执行冲突扫描，断言 `GIT_PERMISSION_DENIED` 与 `GIT_UNAVAILABLE` 均出现且可追溯到对应仓库。
- Slice-2 Playwright 新增窗口详情旅程：
  - 复用 UI 创建出的窗口、迭代和仓库。
  - 模拟后端返回 `GIT_PERMISSION_DENIED` / `GIT_UNAVAILABLE`。
  - 断言类型过滤、阻断级别、建议处理方式、外部 Git 访问处理入口可见，且不会误触发版本同步。

### REFACTOR / REVIEW

- 未改生产领域逻辑，避免把证据任务扩大为功能重构。
- 验收脚本沿用现有本轮唯一时间戳、真实 GitLab 探针和 `ok/no/skip` 报告风格。
- 前端 E2E 复用既有 `selectConflictType`、i18n label 加载和 serial 旅程状态，未引入新页面 fixture。

## 验证

通过命令：

```bash
bash -n scripts/acceptance/run-acceptance.sh
pnpm run typecheck
pnpm i18n:lint
pnpm exec vitest run src/views/release-window/__tests__/ConflictPanel.spec.ts
pnpm exec playwright test e2e/tests/slice-2-full-flow.spec.ts
bash scripts/acceptance/run-acceptance.sh
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- `bash -n` 通过。
- 前端 typecheck 通过。
- i18n lint 通过。
- ConflictPanel Vitest：1 PASS。
- Slice-2 Playwright：23 PASS / 0 FAIL / 0 SKIP。
- 真实 GitLab 验收：134 PASS / 0 FAIL / 0 SKIP。
- 新增 5.9 证据段：`GIT_PERMISSION_DENIED` count=2，`GIT_UNAVAILABLE` count=2，且仓库追溯通过。

## 静态扫描

扫描命令：

```bash
bash scripts/dev/static-scan-topn.sh 10
```

报告路径：`.ai/reports/static-scan/20260520-001612/summary.md`

TopN 处理结论：`git diff --check`、backend SpotBugs、frontend lint、frontend typecheck 均 PASS，TopN 问题摘要为空。

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | 通过 | Git 权限不足/不可达可被后端扫描和前端用户旅程观察 |
| 层级闭环 | 通过 | 验收脚本、真实 GitLab 探针、前端窗口详情旅程和文档均接通 |
| 测试闭环 | 通过 | Playwright、真实 GitLab 验收、typecheck/i18n/Vitest 均通过 |
| 架构闭环 | 通过 | 未改生产分层；证据任务未绕过既有 Port/Adapter |
| 性能闭环 | 通过 | 只新增验收探针，不改变运行时批量扫描复杂度 |
| 文档闭环 | 通过 | 台账、矩阵和本记录已同步 |
| 工作区闭环 | 通过 | 已执行 `git status --short`，变更范围均属于本切片 |

## 未完成项

| 项 | 处理 |
|----|------|
| 带仓库解除挂载真实 GitLab 分支归档复核 | 非本切片目标，仍留在矩阵 P1 队列 |
| 更完整发布计划限制 | 非本切片目标，仍留在矩阵 P1 队列 |

## 经验沉淀

- [x] 不需要新增经验文档；本次遵循既有场景证据脚本与 Slice-2 serial 旅程模式。
