# Slice: SA-003 资源创建叶子分组前端断言

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` SA-003 建立多层分组树
- **日期**：2026-05-20
- **执行者**：AI
- **状态**：已完成

## 选择理由

- 台账要求按场景矩阵补前端用户旅程、后端约束和真实证据差额；SA-003 明确剩余缺口是“前端资源创建时只能选择叶子分组的稳定断言”。
- 该任务覆盖仓库、迭代、发布窗口三个资源归属入口，能直接补齐 Admin Setup 的基础治理证据，避免后续 SA-005/SA-008/SA-009 误挂非叶子分组。
- 范围窄且风险可控：只增强 Playwright 用户旅程和文档，不改变后端校验、数据库结构或现有业务语义。
- 任务本质是验收证据补齐；若现有 UI 不满足断言，再按缺陷处理。实际验证结果显示产品逻辑成立，只需修正 E2E 选择器并沉淀证据。

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | OK | 属于 SA-003 P0 验收焦点：资源只能挂叶子分组 |
| 用户价值 | OK | 管理员在资源创建弹窗中不能选择非叶子分组 |
| 端到端路径 | OK | 覆盖 UI 创建三层分组、仓库/迭代/发布窗口创建入口、前端交互断言 |
| 单一目标 | OK | 只补资源创建叶子分组前端证据，不扩展资源移动或删除保护 |
| 可独立验证 | OK | Slice-1 Playwright 可独立回归 |
| 可回滚 | OK | 影响集中在 `frontend/e2e/tests/slice-1-group-window.spec.ts` 和文档 |
| 依赖明确 | OK | 依赖 Slice-1 前置用例通过 UI 创建三层分组 |
| 风险收敛 | OK | 不新增产品逻辑；只复用现有 `GroupTreeSelect` 的禁用语义 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `frontend/e2e/tests/slice-1-group-window.spec.ts` | 修改 | E2E Test |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | Docs |
| `docs/project-ledger.md` | 修改 | Docs |
| `tasks/records/2026-05-20-sa-003-leaf-group-resource-e2e.md` | 新建 | Docs |

## 执行步骤

### Step 1: RED / 缺口确认

- **缺口**：SA-003 已有后端和验收脚本证据，但前端只覆盖过发布窗口非叶子限制，缺少仓库、迭代、发布窗口三个资源创建入口的统一稳定断言。
- **快速失败证据**：首次运行 `pnpm exec playwright test slice-1-group-window.spec.ts --grep "resource creation only allows leaf groups"` 失败，暴露新增 helper 使用了不符合 Element Plus 实际渲染结构的 `.el-tree-select` 单一选择器。

### Step 2: GREEN

- **测试文件**：`frontend/e2e/tests/slice-1-group-window.spec.ts`
- **实现内容**：
  - 新增 `assertNonLeafGroupDisabled` helper。
  - 新增 `resource creation only allows leaf groups` 用例。
  - 覆盖仓库、迭代、发布窗口三个创建弹窗。
  - 断言 UI 创建的非叶子分组节点展示“有子分组”并带 `aria-disabled=true`。
  - 尝试点击禁用节点后，断言分组选择器仍未选中该非叶子分组。
- **验证结果**：完整 Slice-1 运行 `11 PASS / 0 FAIL / 0 SKIP`。

### Step 3: REFACTOR

- 选择器对齐既有 `selectGroupInDialog` 策略，使用 `.el-tree-select, .el-select` 兼容 `el-tree-select` 渲染后的 Element Plus DOM。
- 仓库弹窗同时存在分组选择器和 Git Provider 选择器；最终断言限定到第一个分组选择器，避免多 `.el-select` 严格模式冲突。
- 提取 `closeVisibleDialog`，避免三个资源入口重复关闭弹窗逻辑。

### Step 4: VERIFY

| 命令 | 结果 |
|------|------|
| `pnpm exec playwright test slice-1-group-window.spec.ts --grep "resource creation only allows leaf groups"` | RED：选择器不匹配，后续已修复 |
| `pnpm run test:e2e:slice-1` | PASS：11 passed |
| `pnpm run typecheck` | PASS |
| `git diff --check` | PASS |
| `bash scripts/dev/static-scan-topn.sh 10` | PASS |

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | OK | 用户不能在仓库、迭代、发布窗口创建入口选择非叶子分组 |
| 层级闭环 | OK | 前端用户旅程覆盖 UI 入口；后端拒绝和数据证据沿用验收脚本基线 |
| 测试闭环 | OK | RED/GREEN 证据存在，Slice-1 回归通过 |
| 架构闭环 | OK | 不改变 DDD 分层、Port/Adapter 或业务状态机 |
| 性能闭环 | OK | 只增加三个弹窗交互断言，无生产运行时影响 |
| 文档闭环 | OK | 已同步场景矩阵、项目台账和本执行记录 |
| 工作区闭环 | OK | 已检查 diff，静态扫描已执行并留痕 |

## 静态扫描

- **扫描命令**：`bash scripts/dev/static-scan-topn.sh 10`
- **报告路径**：`.ai/reports/static-scan/20260520-004838/summary.md`
- **TopN 处理结论**：未发现代码问题；git diff check、backend SpotBugs、frontend ESLint、frontend typecheck 均通过。
- **未解决风险**：SA-003 的资源移动、关联资源删除保护、code 自动生成仍按矩阵保留为 P1/P2。

## 经验沉淀

- [x] 不新增经验文档。沿用既有 Slice-1 serial UI 建数和 Element Plus 选择器兼容策略。
