# Slice: SA-015/SA-016 真实部分失败重试后端/GitLab 强证据

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` 第七节当前推进队列，P1「SA-015 复核扩展」和「SA-016 收尾扩展」
- **日期**：2026-05-17
- **执行者**：AI
- **状态**：已完成

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | 属于场景矩阵 SA-015/SA-016 真实部分失败重试补强 |
| 用户价值 | ✅ | 测试人员/发布经理可复核部分失败 Run 的失败项重试行为 |
| 端到端路径 | ✅ | 覆盖验收脚本、后端 retry API、真实 GitLab 分支和 RunItem/RunStep |
| 单一目标 | ✅ | 只补真实部分失败重试证据，不改产品业务逻辑 |
| 可独立验证 | ✅ | `bash scripts/acceptance/run-acceptance.sh` 可独立验证 |
| 可回滚 | ✅ | 仅脚本与文档变更，可单独回退 |
| 依赖明确 | ✅ | 依赖现有 GitLab seed repo、Settings、Attach Run 和 Run retry 能力 |
| 风险收敛 | ✅ | 使用带时间戳的 feature/release 分支，避免覆盖种子分支 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `scripts/acceptance/run-acceptance.sh` | 修改 | 验收脚本 |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | 场景矩阵 |
| `docs/project-ledger.md` | 修改 | 项目台账 |
| `tasks/records/2026-05-17-sa-015-partial-retry-evidence.md` | 新建 | 任务记录 |

## 执行步骤

### Step 1: RED / 基线

本轮是验收证据补强，不改变产品业务代码；先复核 Attach Run 和 Run retry 的现有实现，确认 `MERGE_BLOCKED` item 可被 retry API 选中。

### Step 2: GREEN

在 `run-acceptance.sh` 新增 SA-015/SA-016 5.8：

- 创建一个真实 GitLab 发布窗口和一个空迭代。
- 为 R1 创建可成功合并的 feature 分支，为 R2 创建 feature/release 双分支并写入冲突 `pom.xml`。
- 分两次把 R1/R2 以 `EXISTING` 分支模式加入同一迭代。
- Attach 后在同一个 `ATTACH_ITERATION` Run 中断言一个 `MERGED` item 和一个 `MERGE_BLOCKED` item 并存。
- 调用 `POST /api/v1/runs/{id}/retry`，只传入失败项 key。
- 断言 retry 新 Run 只有失败项、没有成功仓库项，并包含 `TRY_MERGE` step。

### Step 3: VERIFY

命令：

```bash
bash -n scripts/acceptance/run-acceptance.sh
bash scripts/acceptance/run-acceptance.sh
```

结果：

- 语法检查通过。
- 真实 GitLab 验收通过：112 PASS / 0 FAIL / 0 SKIP。
- 新增 5.8 段通过：`ATTACH_ITERATION::1779027610247` 同时包含成功项和 `MERGE_BLOCKED` 项；retry 新 Run `ATTACH_ITERATION::1779027613682` 只包含失败项，结果为 `MERGE_BLOCKED`。

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | ✅ | 新增脚本路径覆盖真实部分失败 attach 和失败项 retry |
| 层级闭环 | ✅ | 复用既有接口和领域模型，无新增悬空 API |
| 测试闭环 | ✅ | 已完成脚本语法检查和完整真实 GitLab 验收 |
| 架构闭环 | ✅ | 未改动 DDD 分层和业务主流程 |
| 性能闭环 | ✅ | 仅新增验收脚本路径，不影响运行时性能 |
| 文档闭环 | ✅ | 台账、场景矩阵、任务记录已同步 |

## 静态扫描

**扫描命令**：`bash scripts/dev/static-scan-topn.sh 10`
**报告路径**：`.ai/reports/static-scan/20260517-222350/summary.md`
**TopN 处理结论**：未发现 TopN 问题，git diff check、SpotBugs、frontend lint、typecheck 均通过。
**未解决风险**：无本次改动引入风险。

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| 发布报告导出 | 不属于本切片 | `docs/reports/scenario-acceptance-matrix.md` 第七节 |
| 更多真实冲突类型扩展 | 不属于本切片 | `docs/reports/scenario-acceptance-matrix.md` 第七节 |

## 经验沉淀

- [x] 不需要，本次复用既有验收脚本和静态扫描流程。
