# 项目脚本索引

> AI 会话必读：在执行验收测试、环境初始化或代码扫描前，先检查此索引是否有现成脚本。

## 验收测试

| 脚本 | 用途 | 何时使用 |
|------|------|---------|
| [acceptance/run-acceptance.sh](acceptance/run-acceptance.sh) | **全链路验收脚本 v3**（11 场景 / 25+ 验收项） | **验收前必须先运行此脚本**，不要手工逐 API 调试验收 |
| [e2e/init-gitlab.sh](e2e/init-gitlab.sh) | GitLab 种子数据初始化（幂等） | 首次验收或 GitLab 数据被清空后 |
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
| [dev/start-local-env.sh](dev/start-local-env.sh) | 启动本地开发环境（Docker + Backend + Frontend） |
| [dev/static-scan-topn.sh](dev/static-scan-topn.sh) | 静态代码扫描（SpotBugs + ESLint + typecheck） |

## CI

| 脚本 | 用途 |
|------|------|
| [e2e/Dockerfile.test-runner](e2e/Dockerfile.test-runner) | CI 测试运行器镜像 |
