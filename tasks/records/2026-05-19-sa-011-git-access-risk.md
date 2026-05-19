# Slice: SA-011 Git 访问风险类型化

- **蓝图归属**：`docs/reports/scenario-acceptance-matrix.md` SA-011 风险详情 P1 缺口
- **日期**：2026-05-19
- **执行者**：AI
- **状态**：已完成

## 选择理由

台账当前 Top Priority 反复指向 SA-010/SA-011 的发布计划与风险详情，其中 SA-011 剩余明确缺口是 GitLab 不可达/权限失败类风险。该缺口影响发布前风险扫描的可信度：如果 Git 平台权限或连接异常被吞掉、冒泡为扫描失败，或被误判成普通合并冲突，测试人员无法区分业务冲突和外部系统故障。

本切片选择该任务，是因为它符合当前“场景矩阵驱动收口”的主线，不引入新功能主线，也不触碰 RBAC/通知/CI 深集成等台账明确不做事项。实现方式不是简单 catch 后跳过，而是把外部 Git 访问问题建模为一等冲突类型，长期可被 API、前端、验收脚本和报告复用。

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | 通过 | 属于 SA-011 风险详情 P1 缺口 |
| 用户价值 | 通过 | 风险扫描可展示 Git 权限不足/不可达，而不是扫描失败或误判为合并冲突 |
| 端到端路径 | 通过 | Domain/Application/Infrastructure/API DTO 透传/Frontend/Test |
| 单一目标 | 通过 | 仅处理 Git 访问异常风险类型化 |
| 可独立验证 | 通过 | 后端单测、适配器测试、前端组件测试、typecheck、i18n |
| 可回滚 | 通过 | 新增 enum 和展示映射，影响范围集中在冲突检测 |
| 依赖明确 | 通过 | 依赖既有冲突检测和 GitBranchPort |
| 风险收敛 | 通过 | 未改真实 Git 操作副作用，仅改变扫描风险表达 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `backend/releasehub-domain/src/main/java/io/releasehub/domain/version/ConflictType.java` | 修改 | Domain |
| `backend/releasehub-domain/src/main/java/io/releasehub/domain/conflict/ConflictItem.java` | 修改 | Domain |
| `backend/releasehub-application/src/main/java/io/releasehub/application/port/out/GitBranchPort.java` | 修改 | Application Port |
| `backend/releasehub-application/src/main/java/io/releasehub/application/conflict/ConflictDetectionAppService.java` | 修改 | Application |
| `backend/releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/git/GitLabGitBranchAdapter.java` | 修改 | Infrastructure |
| `backend/releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/git/GitHubGitBranchAdapter.java` | 修改 | Infrastructure |
| `frontend/src/api/modules/releaseWindow.ts` | 修改 | Frontend API |
| `frontend/src/views/release-window/ConflictPanel.vue` | 修改 | Frontend UI |
| `frontend/src/i18n/messages/en-US.ts` / `zh-CN.ts` | 修改 | Frontend i18n |
| 后端/前端测试文件 | 修改/新增 | Test |

## 执行步骤

### RED

新增测试先失败：

- `ConflictReportTest`：期望 `GIT_PERMISSION_DENIED`、`GIT_UNAVAILABLE` 两类领域冲突项。
- `ConflictDetectionAppServiceTest`：期望分支状态读取异常和 mergeability 权限失败不再冒泡，产出对应冲突类型。

证据：首次运行 `mvn -pl releasehub-domain,releasehub-application -Dtest=ConflictReportTest,ConflictDetectionAppServiceTest test` 编译失败，缺少新增 enum 和工厂方法。

### GREEN

实现：

- 新增 `GIT_PERMISSION_DENIED`、`GIT_UNAVAILABLE`。
- `MergeabilityResult` 增加失败原因，区分合并冲突、权限不足、不可达和未知错误。
- 冲突扫描改为先安全读取 feature/release 分支状态；Git 访问失败时生成外部风险冲突并停止该仓库的 mergeability 判断。
- GitLab/GitHub adapter 对 401/403 返回权限失败，对网络/未知异常返回不可达。
- 前端冲突面板增加类型过滤、危险级别、处理提示和中英文文案。

### REFACTOR

- 保持 application 层不依赖 Spring Web 异常类型；权限分型依赖 Port 语义，异常兜底只使用保守文本判断。
- 移除冲突扫描中重复的 release 分支状态查询，避免同一仓库一次扫描内重复远程调用。

### VERIFY

通过命令：

```bash
mvn -pl releasehub-domain,releasehub-application -Dtest=ConflictReportTest,ConflictDetectionAppServiceTest test
mvn -pl releasehub-infrastructure -am -Dtest=GitLabGitBranchAdapterTest,GitHubGitBranchAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm exec vitest run src/views/release-window/__tests__/ConflictPanel.spec.ts
pnpm run typecheck
pnpm i18n:lint
bash scripts/dev/static-scan-topn.sh 10
```

结果：

- 后端领域/应用测试：20 PASS。
- GitLab/GitHub adapter 测试：20 PASS。
- 前端 ConflictPanel：1 PASS。
- 前端 typecheck 通过。
- i18n lint 通过。

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | 通过 | Git 访问异常已能进入冲突报告并被前端展示 |
| 层级闭环 | 通过 | Domain/App/Infra/API DTO/Frontend/Test 均已接通 |
| 测试闭环 | 通过 | RED/GREEN 和相关验证命令已记录 |
| 架构闭环 | 通过 | Application 未反向依赖 Spring Web/具体 Git API |
| 性能闭环 | 通过 | 分支状态读取由三次减少为最多两次，未新增 N+1 |
| 文档闭环 | 通过 | 本记录、矩阵和台账已同步 |
| 工作区闭环 | 通过 | 已执行 `git status --short`，变更范围均属于本切片 |

## 静态扫描

扫描命令：

```bash
bash scripts/dev/static-scan-topn.sh 10
```

报告路径：`.ai/reports/static-scan/20260519-234850/summary.md`

TopN 处理结论：`git diff --check`、backend SpotBugs、frontend lint、frontend typecheck 均 PASS，TopN 问题摘要为空。

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| 真实 GitLab 权限不足/不可达验收证据 | 需要真实环境构造 token 权限和网络异常，本切片先完成系统能力 | `docs/reports/scenario-acceptance-matrix.md` SA-011 |
| Playwright 用户旅程复核 | 需要在页面旅程中稳定触发外部 Git 异常 | `docs/reports/scenario-acceptance-matrix.md` SA-011 |

## 经验沉淀

- [x] 不需要
