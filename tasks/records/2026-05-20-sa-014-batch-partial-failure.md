# Slice: SA-014 批量版本更新多仓部分失败

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` SA-014 技术负责人执行版本更新
- **日期**：2026-05-20
- **执行者**：AI
- **状态**：已完成

## 选择理由

- SA-014 已有 Maven 单模块、多模块、Gradle 写回和批量版本更新前端入口。
- 剩余风险是多仓批量执行时一个仓库失败是否会吞掉成功项、是否能在 RunItem/RunStep 中追溯失败原因。
- 现有 `Run.retry` 对版本更新失败缺少 replay 请求上下文，本切片只补“多仓部分失败真实证据”，不把版本更新失败重试混入同一变更。

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `scripts/acceptance/run-acceptance.sh` | 修改 | Acceptance |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | Docs |
| `docs/project-ledger.md` | 修改 | Docs |
| `tasks/records/2026-05-20-sa-014-batch-partial-failure.md` | 新增 | Tasks |

## 执行步骤

### Step 1: RED / 缺口确认

- `executeBatchVersionUpdate` 已按仓库创建 RunItem，但场景矩阵缺少真实 GitLab 部分失败证据。
- `RunAppService.retry` 当前只安全覆盖编排/attach 类 RunItem，版本更新失败重试需要额外保存原始请求上下文，暂不在本切片实现。

### Step 2: GREEN

- `run-acceptance.sh` 升级到 v3.15。
- 新增 SA-014 8.5 批量版本更新多仓部分失败探针：
  - 独立创建发布窗口。
  - 为 R1/R2 准备同一 release 分支。
  - R1 使用有效 `pom.xml` 更新到 `1.6.0`。
  - R2 使用缺失的 `missing-pom.xml` 制造失败。
  - 断言总体 Run 为 `FAILED`，RunItem 同时包含 `VERSION_UPDATE_SUCCESS` 和 `VERSION_UPDATE_FAILED`。
  - 断言失败步骤消息包含缺失 POM 路径。
  - 断言成功仓库 release 分支存在版本更新 commit 且 `pom.xml` 写回 `1.6.0`。
- 修正 SA-014 8.3 Maven 多模块探针：Attach 后在本轮 release 分支准备 root/module fixture，再执行版本更新，避免 fixture 只写入 feature 分支。

### Step 3: VERIFY

| 命令 | 结果 |
|------|------|
| `bash -n scripts/acceptance/run-acceptance.sh` | PASS |
| `git diff --check` | PASS |
| `bash scripts/acceptance/run-acceptance.sh` | PASS：159 passed, 0 failed, 0 skipped |
| `bash scripts/dev/static-scan-topn.sh 10` | PASS：TopN 未发现问题 |

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | OK | 批量版本更新部分失败时成功项真实写回，失败项原因可见 |
| 层级闭环 | OK | 复用既有批量版本更新 API、Run、RunItem 和 GitLab file adapter |
| 测试闭环 | OK | 全量真实 GitLab 证据复核通过 |
| 架构闭环 | OK | 未改变产品 API；版本更新失败重试保留为独立能力 |
| 文档闭环 | OK | 已同步场景矩阵、项目台账和本执行记录 |
| 工作区闭环 | OK | `bash -n`、`git diff --check`、全量 acceptance 和静态扫描均已通过；扫描报告：`.ai/reports/static-scan/20260520-235530/summary.md` |

## 经验沉淀

- [x] 不新增经验文档。本轮经验已记录在 RED 和 GREEN 小节。
