# 项目脚本索引

> AI 会话必读：在执行验收测试、环境初始化或代码扫描前，先检查此索引是否有现成脚本。

## 验收测试

| 脚本 | 用途 | 何时使用 |
|------|------|---------|
| [acceptance/run-acceptance.sh](acceptance/run-acceptance.sh) | **全链路验收脚本 v3**（11 场景 / 25+ 验收项） | **验收前必须先运行此脚本**，不要手工逐 API 调试验收 |
| [acceptance/sa014-empty-repo-version-evidence.sh](acceptance/sa014-empty-repo-version-evidence.sh) | SA-014 真实 GitLab 空仓库版本解析诊断证据，创建空项目、通过系统纳管、复核 `initial-version` / `sync-version` 诊断并输出报告 | 需要证明空仓库不会被误判为正常版本、不会填充假版本、不会阻塞仓库列表时 |
| [acceptance/sa002-safe-cleanup.sh](acceptance/sa002-safe-cleanup.sh) | SA-002 存量数据安全清理 dry-run 报告，输出带数据源口径、数据命名空间、复核批次、资产范围、保留策略、应用入口、执行前检查、执行后复核和人工复核决策的动作清单，不直接改库 | 验收前需要把 token 明文、BranchCreationMode、featureBranch、cloneUrl、DRAFT 残留转成可进入 `POST /api/v1/data-quality/cleanup-review` 的复核计划，并区分 `API_VISIBLE_ASSETS`、`DB_AUDIT_ASSETS`、`REVIEW_QUEUE_ACTIONS` 以及本轮/历史/业务资产时 |
| [e2e/init-gitlab.sh](e2e/init-gitlab.sh) | GitLab 种子数据初始化（幂等） | 首次验收或 GitLab 数据被清空后 |
| [e2e/reset-gitlab-seed-branches.sh](e2e/reset-gitlab-seed-branches.sh) | 清理 GitLab 种子仓库累积分支，默认 dry-run，`--execute` 才删除，并输出 `summary.md`、`branches.md`、`branches.jsonl` | release/feature 临时分支累积影响 clean-room 复现，且需要保留 dry-run/execute/复跑证据时 |
| [e2e/run-vertical-slices.sh](e2e/run-vertical-slices.sh) | CI 环境垂直切片测试 | CI 流水线 |

### 为什么必须先运行 run-acceptance.sh？

手工逐 API 调试验收容易踩的坑：
1. GitLab 种子仓库为空 → 编排 produce 0 items
2. GitLab Settings 未配置 → 部分 API 返回 500
3. 仓库 cloneUrl 与 GitLab 项目路径不匹配 → 分支操作 404
4. repo ID 来自过期数据库 → 查询返回空
5. 冲突检测/编排 API 依赖前置数据（versionInfo、featureBranch）

此脚本知道所有前置条件，按正确顺序完成全部步骤。绕过它在 v0.1.10 验收中已浪费大量排查时间。

## 开发环境

| 脚本 | 用途 |
|------|------|
| [dev/start-local-env.sh](dev/start-local-env.sh) | 统一启停本地开发环境（`start|hold|stop|restart|status`，Docker + Backend + Frontend） |
| [dev/cleanup-dev-database.sh](dev/cleanup-dev-database.sh) | 本地开发库业务数据清理脚本，默认 dry-run，`--execute` 后清理 `release_hub` 与历史 `public` schema 中的业务表，保留 Flyway 元数据、用户和系统设置 |
| [dev/static-scan-topn.sh](dev/static-scan-topn.sh) | 静态代码扫描（SpotBugs + ESLint + typecheck） |

## CI

| 脚本 | 用途 |
|------|------|
| [e2e/Dockerfile.test-runner](e2e/Dockerfile.test-runner) | CI 测试运行器镜像 |
