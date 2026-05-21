# Slice: SA-010 解除挂载 GitLab 分支归档复核

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` 七、当前推进队列：SA-010 发布计划与解除挂载收口
- **日期**：2026-05-20
- **执行者**：AI
- **状态**：已完成

## 选择理由

- 台账和矩阵同时把“带仓库解除挂载真实 GitLab 分支归档复核”列为 SA-010 P1 缺口。
- 现有后端单测已验证 `AttachAppService.detach()` 会调用 `archiveBranch(release/<windowKey>, "unpublished")`，前端 Playwright 也已覆盖用户点击路径；剩余风险在真实 GitLab 外部状态是否成立。
- 该任务不引入新产品能力，只把既有业务语义补成强证据，符合 v0.1.11 验证闭环主线。
- 独立窗口/迭代验证不会影响后续主线窗口的冲突扫描、编排和版本更新。

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | OK | 属于 SA-010 解除挂载真实 GitLab 证据缺口 |
| 用户价值 | OK | 发布经理解除挂载后，系统对 release 分支的外部副作用可审计 |
| 端到端路径 | OK | 覆盖 API、应用服务、GitLab adapter、真实 GitLab 分支状态和文档 |
| 单一目标 | OK | 只补 detach 后 release 分支归档证据，不扩展发布计划 UI |
| 可独立验证 | OK | `run-acceptance.sh` 4.2 可独立输出 PASS/FAIL |
| 可回滚 | OK | 影响集中在验收脚本和文档 |
| 依赖明确 | OK | 依赖既有 `AttachAppService.detach()` 和 GitLab `archiveBranch` 语义 |
| 风险收敛 | OK | 使用独立窗口/迭代，不破坏后续 SA-011/SA-013/SA-014 主线 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `scripts/acceptance/run-acceptance.sh` | 修改 | Acceptance |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | Docs |
| `docs/project-ledger.md` | 修改 | Docs |
| `tasks/records/2026-05-20-sa-010-detach-gitlab-archive.md` | 新建 | Docs |

## 执行步骤

### Step 1: RED / 缺口确认

- **缺口**：验收脚本只验证 attach 后 release 分支存在；detach 后是否真实归档到 GitLab 没有脚本证据。
- **代码基线**：`AttachAppService.detach()` 已按每个仓库调用 `archiveBranch(release/<windowKey>, "unpublished")`，`GitLabGitBranchAdapter.archiveBranch()` 会创建 `archive/unpublished/release-<windowKey>` 并删除原分支。

### Step 2: GREEN

- `run-acceptance.sh` 升级到 v3.13。
- 新增 SA-010 4.2：创建独立窗口和单仓迭代，attach 后验证 `release/<windowKey>` 存在。
- 调用既有 `POST /release-windows/{id}/detach` 后验证：
  - WindowIteration 列表为空。
  - GitLab 原 `release/<windowKey>` 返回不存在。
  - GitLab `archive/unpublished/release-<windowKey>` 存在。

### Step 3: REFACTOR / Review

- 复用既有 `gitlab_branch_state()` 和仓库 URL 读取逻辑，不新增重复 GitLab API 封装。
- 新增窗口/迭代不复用主线 `WINDOW_ID`/`ITER_KEY`，避免对后续冲突扫描和版本更新产生副作用。
- attach 断言同时检查 API `success=true` 和 `hasErrors=false`，避免错误响应被误判为通过。

### Step 4: VERIFY

| 命令 | 结果 |
|------|------|
| `bash -n scripts/acceptance/run-acceptance.sh` | PASS |
| `git diff --check` | PASS |
| `bash scripts/acceptance/run-acceptance.sh` | 142 PASS / 0 FAIL / 0 SKIP |
| `bash scripts/dev/static-scan-topn.sh 10` | PASS |

关键新增证据：

- attach 后 release 分支存在：`release/RW-20260520-B1F5`
- detach 后原 release 分支已删除：`release/RW-20260520-B1F5`
- detach 后归档分支存在：`archive/unpublished/release-RW-20260520-B1F5`

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | OK | detach 后真实 GitLab 分支归档结果已可验证 |
| 层级闭环 | OK | 接通 API、应用服务、GitLab adapter 和外部 GitLab 状态证据 |
| 测试闭环 | OK | 脚本新增独立证据复核段；完整场景验收仍以外部 Playwright 真实页面旅程为准 |
| 架构闭环 | OK | 不改变 DDD 分层，不在脚本中替代业务行为，只复核外部证据 |
| 性能闭环 | OK | 新增单仓独立窗口验证，不引入批量远程调用放大 |
| 文档闭环 | OK | 已同步场景矩阵、项目台账和本执行记录 |
| 工作区闭环 | OK | 已检查 diff、运行静态扫描并保留报告 |

## 静态扫描

- **扫描命令**：`bash scripts/dev/static-scan-topn.sh 10`
- **报告路径**：`.ai/reports/static-scan/20260520-002936/summary.md`
- **TopN 处理结论**：TopN 未发现代码问题；git diff check、backend SpotBugs、frontend lint、frontend typecheck 均通过。
- **未解决风险**：SA-010 剩余 P1 只保留更完整发布计划限制；同分组约束和更多前端复核按矩阵后续推进。

## 经验沉淀

- [x] 不新增经验文档。沿用既有验收脚本真实 GitLab 证据模式，未产生新的可复用坑点。
