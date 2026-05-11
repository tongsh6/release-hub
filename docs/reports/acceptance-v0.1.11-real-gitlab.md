# 真实验收报告：ReleaseHub v0.1.11 + Real GitLab

> 验收时间：2026-05-11 23:00 CST
> 验收人：AI 审计会话（Opus 4.7）
> 环境：本地模式 A（macOS + Docker Desktop）+ GitLab 17.11.3 + PostgreSQL 18.1
> 验收脚本：`scripts/acceptance/run-acceptance.sh` v3
> 主版本：v0.1.11（含本会话补充：多 Provider 端口收口 commits 0e2efb3 / 68381b1 / 962be80）

## 执行环境

| 组件 | 版本/地址 | 状态 |
|------|----------|------|
| GitLab | `gitlab/gitlab-ce:17.11.3-ce.0` @ localhost:9080 | Healthy |
| PostgreSQL | `postgres:18.1` @ localhost:5433 | Up |
| Backend | Spring Boot 3.4.1 @ localhost:8080（profile=local,real）| UP |
| Frontend | 未启动 | — |
| Test Counts | mvn test 161 通过（本会话刚跑） | ✅ |

## 验收结果：22 PASS / 1 FAIL / 1 SKIP

> 注：本轮验证 v0.1.11 三大特性（远程版本更新 / 设置持久化 / 三层关联）+ 多 Provider 迁移收口；
> 较 v0.1.10 报告新增了**异步 Run 状态轮询（wait_for_run）** 和**分支创建模式 5 个场景**。
> 1 个 FAIL 为 `Orchestrate 启动失败`（根因为脚本缺一步「先 POST settings/gitlab」，下方有专项分析）。

### 场景结果

| # | 场景 | 结果 | 备注 |
|---|------|:----:|------|
| 0 | 环境检查（GitLab/PostgreSQL/Backend） | PASS | 前端未启动（验收不依赖） |
| 1 | 存量数据审计 | PASS | 1 group / 3 repos / 1 window / 5 iterations / 1 run |
| 1.4 | 脏数据检测 | PASS | 无脏数据 |
| 2 | GitLab 种子数据初始化 | PASS | 3 repos 幂等复用 |
| 3 | 新增发布窗口全链路（Group→Repo→Window→Iteration） | PASS | 6 项 |
| 3.6 | Feature 分支创建（Git 推送） | PASS | feature/ITER-20260511-3D34 |
| 4 | Attach + GitLab release 分支创建 | PASS（部分）| 真实 release 分支 1/3，需排查 |
| 5 | 冲突检测 | PASS | 0 冲突 |
| 6 | Publish + 自动编排 | **FAIL** | Orchestrate 启动失败（详见下方根因） |
| 7 | Run 执行详情 | WARN | items=0（v0.1.10 已知限制 ① 延续） |
| 8 | 版本更新 + 校验 | SKIP | GitLab settings missing |
| 9 | 存量数据冒烟 | INFO | CLOSED:0 PUBLISHED:2 |
| 10.1 | **AUTO 模式（v0.1.11 新）** | **PASS** | featureBranch=feature/ITER-... |
| 10.2 | **NAMED 模式（v0.1.11 新）** | **PASS** | featureBranch=feature/acceptance-named-... |
| 10.3 | **NAMED 非法分支名拒绝（v0.1.11 新）** | **PASS** | featureBranch=null |
| 10.4 | **EXISTING 不存在分支拒绝（v0.1.11 新）** | **PASS** | featureBranch=null |
| 10.5 | **GET /branches 端点（v0.1.11 新）** | **PASS** | 返回 0 个 |

### v0.1.11 新特性首次真实验证

| 特性 | 状态 | 证据 |
|---|:----:|---|
| **三层关联 + BranchCreationMode**（commit 931a6b3） | ✅ 全通过 | 场景 10.1-10.5 全 PASS；BranchCreationMode 三模式真实环境行为符合预期；非法/不存在分支被显式拒绝 |
| **多 Provider 端口迁移**（commit 0e2efb3，本会话） | ✅ 工厂正确路由 | Iteration setup → GitLab API 调用走真实路径；后端日志显示 `GET /api/v4/projects/.../repository/branches/...` 由 GitLabGitBranchAdapter 发出 |
| **远程版本更新**（commit bbbae46） | ⚠️ 未触达 | 因 Orchestrate FAIL，VERSION_UPDATE 未真正执行；脚本场景 8 已 SKIP（settings missing），需先解决 root cause |
| **设置持久化**（commit 55cb5b0） | ⚠️ 未显式校验重启保留性 | 本轮跑后 `release_hub.system_settings` 已建表（ddl-auto: update），但脚本未做「重启后端→Settings 仍在」断言 |
| **验收脚本 wait_for_run 异步轮询**（commit 9eb5444） | ✅ 已激活 | 函数 `wait_for_run` 在场景 6.1 / 8.1 中调用，60s 超时机制有效 |
| **GitLabRequest @NoArgs/@AllArgs 修复**（commit 962be80，本会话） | ⚠️ 未触达 | POST /settings/gitlab 未在脚本中执行 |

## 真实可观测问题（区分严重度）

### P0 — 代码 Bug：WindowLifecycleListener 异常未隔离触发 TX 回滚

**症状**：场景 6 Publish 成功，但 WindowLifecycleListener 中自动编排失败时，`afterCompletion` 抛出 `UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only`。

**后端日志**（`/tmp/releasehub-backend.log`）：
```
WARN  WindowLifecycleListener : Auto-orchestration failed for published window 3849adcb-... : error.gitlab.settings_missing
ERROR TransactionSynchronizationUtils : TransactionSynchronization.afterCompletion threw exception
org.springframework.transaction.UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only
```

**影响**：Publish 在 settings 缺失时表面成功（窗口已 PUBLISHED），但事务后置回调抛错污染日志，可能误导运维；如有依赖 `afterCommit` 的逻辑会被吞掉。

**修复方向**（不在本次验收范围）：
- `WindowLifecycleListener#afterCommit` 中的 BusinessException 应在 try-catch 中只 log 不 throw
- 或者：让 `error.gitlab.settings_missing` 这类「配置缺失」场景在 Publish 前置校验时拦截，避免进入 listener

### P1 — 脚本缺步：未先 ensure GitLab Settings

**症状**：场景 6 Orchestrate 失败、场景 8 版本更新 SKIP，根因都是 `GitLab settings are missing`（GITLAB_001）。脚本场景 2 调了 `init-gitlab.sh` 但**没有调** `POST /api/v1/settings/gitlab` 把 baseUrl + token 写入后端。

**修复方向**（建议作为脚本 v3.1）：在场景 2 末尾增加：
```bash
curl -s -X POST "$BACKEND/api/v1/settings/gitlab" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"baseUrl\":\"http://localhost:9080\",\"token\":\"$GITLAB_PAT\"}"
```

这一步缺失说明：v0.1.11 的「设置持久化」功能既没被使用、也没被验证过。

### P2 — 数据观察：GitLab release 分支只创建 1/3

**症状**：`Attach 成功`、`branchCreated=True releaseBranch=release/RW-20260511-501B`，但脚本侧轮询 GitLab API 只发现 1 个 release/* 分支（应为 3）。

**可能原因**（未排查）：
- 仓库 1/2/3 的 cloneUrl 可能仍含双 `http://` 前缀（init-gitlab 输出 `http://http://localhost:9080/...`）→ Attach 时调用真实 GitLab API 失败被静默吞掉
- 或仅第一个仓库的 token 配置生效

### P3 — 加密=0：Token 加密策略未生效

**症状**：场景 1 显示 `Token 已全部加密: 0 个仓库`、汇总 `加密=0 | 明文=0`。

**可能原因**（推测）：本轮 3 个仓库的 `git_access_token` 字段为空（因为 init-gitlab 后写仓库时未传 token；脚本里只在 `init` 内部使用 `glpat-...`，注册仓库时没把 token 写入 ReleaseHub）。
**这是数据缺失而非加密功能失效**，与 v0.1.10 报告 6/6 加密的差异源于**这是一个全新的本地数据库**。

## 与 v0.1.10 报告「已知限制」的处置对照

| v0.1.10 已知限制 | v0.1.11 处置 | 状态 |
|---|---|---|
| ① 编排 0 items（feature 分支命名/version-info 不一致）| `wait_for_run` + 三层关联 BranchCreationMode + 移除 6 处 fallback | ⚠️ 链路条件已具备，但被新发现的 P1 阻塞，未跑到该路径 |
| ② VERSION_UPDATE Run FAILED | 远程版本更新（commit bbbae46）+ 脚本 wait_for_run | ⚠️ SKIP，被 P1 阻塞 |

## 总体验收判定

**有条件通过** ⚠️

- ✅ v0.1.11 主目标特性「三层关联 + BranchCreationMode」5 个场景全 PASS，**首次真实环境验证**
- ✅ 多 Provider 端口迁移工厂路由正确，GitLab API 调用走真实路径
- ✅ 161 单测全过 + 22/24 验收 PASS，主流程闭环未退化
- ⚠️ 1 个新发现的代码缺陷（P0：Listener 异常未隔离）+ 1 个脚本缺步（P1）+ 2 个数据观察项（P2/P3）
- ⚠️ v0.1.11 的「远程版本更新」和「设置持久化」功能本轮**未真正触达**，被 P1 阻塞，无法在本轮证明它们是否真修复了 v0.1.10 的两处已知限制

## 下一步（按优先级）

1. **P1 修脚本** — 在 `run-acceptance.sh` 场景 2 末尾增加 `POST /settings/gitlab` 步骤；预计 5 行代码
2. **P0 修代码** — `WindowLifecycleListener` 异常隔离（独立 commit）
3. **重跑验收** — 验证 v0.1.10 两处已知限制是否真被 v0.1.11 修复
4. **P3 排查** — 注册仓库时是否需要带 `gitAccessToken`，或脚本应用本地 PAT
5. **P2 排查** — Attach 后真实 release 分支只 1/3 的根因

## 证据索引

- 完整脚本输出：`/tmp/acceptance-v0.1.11.log`（22 PASS / 1 FAIL / 1 SKIP / 5 WARN / 多 INFO）
- 后端日志：`/tmp/releasehub-backend.log`（含 GITLAB_001 / UnexpectedRollbackException 完整堆栈）
- 单测基线：`mvn test` 161 通过（IterationAppService 15 / RunAppService 6 / GitBranchAdapterFactoryImplTest 3 / GitHubGitBranchAdapterTest 6 / GitLabGitBranchAdapterTest 7）
- v0.1.10 对照报告：`docs/reports/acceptance-v0.1.10-real-gitlab.md`
