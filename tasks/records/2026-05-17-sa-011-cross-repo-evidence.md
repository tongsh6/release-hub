# Slice: SA-011 CROSS_REPO_VERSION_MISMATCH 后端/GitLab 强证据

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` 第七节当前推进队列，P1「SA-010/SA-011 发布计划与风险详情」
- **日期**：2026-05-17
- **执行者**：AI
- **状态**：已完成

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | 属于场景矩阵 SA-011 后端/GitLab 强证据补强 |
| 用户价值 | ✅ | 测试人员看到的跨仓版本风险具备后端与 GitLab 可复核证据 |
| 端到端路径 | ✅ | 覆盖验收脚本、后端 API、GitLab 分支、冲突扫描和文档 |
| 单一目标 | ✅ | 仅补 `CROSS_REPO_VERSION_MISMATCH` 强证据 |
| 可独立验证 | ✅ | `bash scripts/acceptance/run-acceptance.sh` 独立验证 |
| 可回滚 | ✅ | 仅脚本与文档变更，可单独回退 |
| 依赖明确 | ✅ | 依赖现有 GitLab seed repo、Settings、仓库纳管和冲突检测能力 |
| 风险收敛 | ✅ | 临时修改仓库初始版本后立即复原或重新同步，避免污染后续场景 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `scripts/acceptance/run-acceptance.sh` | 修改 | 验收脚本 |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | 场景矩阵 |
| `docs/project-ledger.md` | 修改 | 项目台账 |
| `tasks/records/2026-05-17-sa-011-cross-repo-evidence.md` | 新建 | 任务记录 |

## 执行步骤

### Step 1: RED / 基线

本轮是验收证据补强，不改变产品业务代码；先用 `bash -n scripts/acceptance/run-acceptance.sh` 确认改动前脚本语法基线可解析。

### Step 2: GREEN

在 `run-acceptance.sh` 新增 SA-011 5.7：

- 临时设置 R1/R2 初始版本为 `2.0.0` / `3.0.0`。
- 创建同一迭代并生成不同 targetVersion。
- 复原或重新同步仓库初始版本。
- 直查两仓 GitLab feature/release 分支。
- 触发冲突扫描并断言 `CROSS_REPO_VERSION_MISMATCH`。

### Step 3: VERIFY

命令：

```bash
bash -n scripts/acceptance/run-acceptance.sh
bash scripts/acceptance/run-acceptance.sh
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- 语法检查通过。
- 真实 GitLab 证据复核通过：99 PASS / 0 FAIL / 0 SKIP。
- 静态扫描通过：`.ai/reports/static-scan/20260517-220354/summary.md`。

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | ✅ | SA-011 跨仓版本风险已有后端/GitLab 强证据 |
| 层级闭环 | ✅ | 无新增悬空 API 或 DTO |
| 测试闭环 | ✅ | 证据复核脚本真实执行通过 |
| 架构闭环 | ✅ | 未改动 DDD 分层和业务主流程 |
| 性能闭环 | ✅ | 仅新增验收脚本路径，不影响运行时性能 |
| 文档闭环 | ✅ | 台账、场景矩阵、任务记录已同步 |
| 工作区闭环 | ✅ | `git status --short` 已检查 |

## 静态扫描

**扫描命令**：`bash scripts/dev/static-scan-topn.sh 10`
**报告路径**：`.ai/reports/static-scan/20260517-220354/summary.md`
**TopN 处理结论**：未发现 TopN 问题，git diff check、SpotBugs、frontend lint、typecheck 均通过。
**未解决风险**：无本次改动引入风险。

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| 真实部分失败重试 | 不属于本切片 | `docs/reports/scenario-acceptance-matrix.md` 第七节 |
| 更多真实冲突类型扩展 | 不属于本切片 | `docs/reports/scenario-acceptance-matrix.md` 第七节 |

## 经验沉淀

- [x] 不需要，本次复用既有验收脚本和静态扫描流程。
