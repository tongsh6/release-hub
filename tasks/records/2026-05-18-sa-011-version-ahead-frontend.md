# Slice: SA-011 REPO_AHEAD / SYSTEM_AHEAD 前端风险详情观察

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` 第七节当前推进队列，P1「SA-010/SA-011 发布计划与风险详情」
- **日期**：2026-05-18
- **执行者**：AI
- **状态**：已完成

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | 属于场景矩阵 SA-011 更多真实冲突类型前端观察补强 |
| 用户价值 | ✅ | 测试人员能在窗口详情风险面板区分仓库版本较新和系统版本较新，并看到处理建议 |
| 端到端路径 | ✅ | 覆盖 Frontend E2E、场景矩阵、项目台账和任务记录 |
| 单一目标 | ✅ | 仅补 `REPO_AHEAD` / `SYSTEM_AHEAD` 前端观察与同步请求语义 |
| 可独立验证 | ✅ | `pnpm exec playwright test e2e/tests/slice-2-full-flow.spec.ts` 可独立验证 |
| 可回滚 | ✅ | 变更集中在 Playwright 用例和文档，可按文件回退 |
| 依赖明确 | ✅ | 依赖既有 `ConflictPanel` 枚举展示、`ReleaseWindowDetail` 冲突解决事件和前一切片后端/GitLab 强证据 |
| 风险收敛 | ✅ | 未新增业务 API 或组件逻辑；只用页面路由拦截构造风险报告，不改真实业务数据 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `frontend/e2e/tests/slice-2-full-flow.spec.ts` | 修改 | Frontend E2E |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | 场景矩阵 |
| `docs/project-ledger.md` | 修改 | 项目台账 |
| `tasks/records/2026-05-18-sa-011-version-ahead-frontend.md` | 新建 | 任务记录 |

## 执行步骤

### Step 1: RED

新增 Slice-2 用例，要求窗口详情风险面板展示 `REPO_AHEAD` / `SYSTEM_AHEAD` 类型分布、版本差异、阻断级别、建议处理方式，并确认版本领先类冲突可走应用内同步版本。

证据：

```bash
pnpm run test:e2e -- slice-2-full-flow.spec.ts -g "SA-011 frontend path surfaces repo/system version-ahead details"
```

初次运行中新增用例失败：普通 click 未稳定触发 Element Plus link button 的确认框，RED 成立。

### Step 2: GREEN

- 同步加载 `conflict.types.REPO_AHEAD` 和 `conflict.types.SYSTEM_AHEAD` i18n label。
- 新增 `SA-011 frontend path surfaces repo/system version-ahead details`。
- 测试中复用同一个 UI-created serial 旅程产生的窗口、迭代和仓库上下文，通过路由拦截构造版本领先风险报告。
- 对 Element Plus link button 采用与既有用例一致的 DOM click，确认 `resolve-conflict` 请求体为 `resolution=USE_SYSTEM`。

### Step 3: VERIFY

命令：

```bash
pnpm run typecheck
pnpm i18n:lint
pnpm exec playwright test e2e/tests/slice-2-full-flow.spec.ts
```

结果：

- `vue-tsc --noEmit` 通过。
- i18n lint 通过。
- Slice-2 Playwright：22 PASS / 0 FAIL / 0 SKIP。

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | ✅ | 测试人员可在窗口详情看到两类版本领先风险并确认同步版本语义 |
| 层级闭环 | ✅ | 既有 API/DTO/组件已支持该类型，本切片补齐前端旅程证据 |
| 测试闭环 | ✅ | RED/GREEN 证据和目标回归已记录 |
| 架构闭环 | ✅ | 未把后端业务真相推导写入前端，只展示后端返回的冲突类型和版本字段 |
| 性能闭环 | ✅ | 未新增运行时请求或轮询 |
| 文档闭环 | ✅ | 场景矩阵、台账和任务记录已同步 |
| 工作区闭环 | ✅ | `git status --short` 已检查，未发现无关用户改动 |

## 静态扫描

**扫描命令**：`bash scripts/dev/static-scan-topn.sh 10`
**报告路径**：`.ai/reports/static-scan/20260519-000010/summary.md`
**TopN 处理结论**：未发现 TopN 问题；git diff check、backend SpotBugs、frontend ESLint、frontend typecheck 均通过。
**未解决风险**：无本次改动引入风险。

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| GitLab 不可达/权限失败类风险 | 属于外部系统异常扩展，本切片只补已具备后端/GitLab 强证据的版本领先风险前端观察 | `docs/reports/scenario-acceptance-matrix.md` 第七节 |

## 经验沉淀

- [x] 不需要，本次复用既有 Playwright 路由拦截和 Element Plus link button DOM click 模式。
