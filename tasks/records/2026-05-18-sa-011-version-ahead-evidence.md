# Slice: SA-011 REPO_AHEAD / SYSTEM_AHEAD 后端/GitLab 强证据

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` 第七节当前推进队列，P1「SA-010/SA-011 发布计划与风险详情」
- **日期**：2026-05-18
- **执行者**：AI
- **状态**：已完成

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | 属于场景矩阵 SA-011 更多真实冲突类型后端/GitLab 强证据补强 |
| 用户价值 | ✅ | 测试人员看到的版本风险不再只有泛化 `MISMATCH`，可区分仓库领先和系统领先 |
| 端到端路径 | ✅ | 覆盖 Application 冲突分类、真实 GitLab 验收脚本、单测和文档 |
| 单一目标 | ✅ | 仅补 `REPO_AHEAD` / `SYSTEM_AHEAD` 分类和证据 |
| 可独立验证 | ✅ | 目标单测和 `bash scripts/acceptance/run-acceptance.sh` 可独立验证 |
| 可回滚 | ✅ | 变更集中在冲突分类服务、验收脚本和文档，可按文件回退 |
| 依赖明确 | ✅ | 依赖既有 `VersionDeriverUseCase`、版本冲突解决 API、GitLab seed repo |
| 风险收敛 | ✅ | 临时版本设置会复原；新增分类后已同步黄金路径解决候选，避免阻断后续场景 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `backend/releasehub-application/src/main/java/io/releasehub/application/conflict/ConflictDetectionAppService.java` | 修改 | Application |
| `backend/releasehub-application/src/test/java/io/releasehub/application/conflict/ConflictDetectionAppServiceTest.java` | 修改 | Test |
| `scripts/acceptance/run-acceptance.sh` | 修改 | 验收脚本 |
| `docs/reports/scenario-acceptance-matrix.md` | 修改 | 场景矩阵 |
| `docs/project-ledger.md` | 修改 | 项目台账 |
| `tasks/records/2026-05-18-sa-011-version-ahead-evidence.md` | 新建 | 任务记录 |

## 执行步骤

### Step 1: RED

新增 `ConflictDetectionAppServiceTest` 用例，要求版本提取值高于系统记录时产出 `REPO_AHEAD`，系统记录高于版本提取值时产出 `SYSTEM_AHEAD`。

证据：

```bash
mvn -pl releasehub-application -am -Dtest=ConflictDetectionAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

初次运行编译失败：`ConflictDetectionAppService` 构造器缺少 `VersionDeriverUseCase`，RED 成立。

### Step 2: GREEN

- `ConflictDetectionAppService` 注入既有 `VersionDeriverUseCase`。
- 版本不一致时先比较版本：`system < repo` → `REPO_AHEAD`，`system > repo` → `SYSTEM_AHEAD`，不可细分时保留 `MISMATCH`。
- `run-acceptance.sh` v3.11 新增 SA-011 5.8：真实 GitLab feature 分支分别写入 `1.2.0-SNAPSHOT` 和 `1.0.0-SNAPSHOT`，扫描断言 `REPO_AHEAD` / `SYSTEM_AHEAD`。
- 黄金路径版本冲突解决候选同步覆盖 `MISMATCH`、`REPO_AHEAD`、`SYSTEM_AHEAD`、`CROSS_REPO_VERSION_MISMATCH`。

### Step 3: VERIFY

命令：

```bash
bash -n scripts/acceptance/run-acceptance.sh
mvn -pl releasehub-application -am -Dtest=ConflictDetectionAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
bash scripts/acceptance/run-acceptance.sh
```

结果：

- 脚本语法通过。
- `ConflictDetectionAppServiceTest`：9 PASS。
- 真实 GitLab 验收：126 PASS / 0 FAIL / 0 SKIP。

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | ✅ | SA-011 版本领先类风险已能被后端分类并由真实 GitLab 证据复核 |
| 层级闭环 | ✅ | 未新增 API/DTO；现有前端类型和 i18n 已覆盖这些枚举 |
| 测试闭环 | ✅ | RED/GREEN 证据和完整验收证据已记录 |
| 架构闭环 | ✅ | 复用既有版本比较用例接口，未引入跨层依赖 |
| 性能闭环 | ✅ | 每个版本冲突只增加一次本地版本比较，无额外远程调用 |
| 文档闭环 | ✅ | 台账、场景矩阵、任务记录已同步 |
| 工作区闭环 | ✅ | `git status --short` 已检查，未发现无关用户改动 |

## 静态扫描

**扫描命令**：`bash scripts/dev/static-scan-topn.sh 10`
**报告路径**：`.ai/reports/static-scan/20260518-230727/summary.md`
**TopN 处理结论**：未发现 TopN 问题；git diff check、backend SpotBugs、frontend lint、frontend typecheck 均通过。
**未解决风险**：无本次改动引入风险。

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| `REPO_AHEAD` / `SYSTEM_AHEAD` 前端专项观察 | 本切片优先补后端/GitLab 强证据，前端已有通用版本冲突展示能力 | `docs/reports/scenario-acceptance-matrix.md` 第七节 |
| GitLab 不可达/权限失败类冲突 | 属于 Phase 2 外部系统异常扩展 | `docs/reports/scenario-acceptance-matrix.md` 第六节 |

## 经验沉淀

- [x] 不需要，本次复用既有 E2E 自动化、分支命名和静态扫描经验。
