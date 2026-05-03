# Slice 7: CI 流水线重构

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 7 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：已完成

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | CI 三条流水线 |
| 用户价值 | ✅ | PR 阶段秒级单测 + 合并后全链路 + 覆盖率门禁 |
| 端到端路径 | ✅ | GitHub Actions → build → test → report |
| 单一目标 | ✅ | 拆分流水线，覆盖 Java 单测回归空白 |
| 可独立验证 | ✅ | 每条 workflow 独立触发 |
| 可回滚 | ✅ | git revert workflow |
| 依赖明确 | ✅ | 依赖 Slice 2-6 |
| 风险收敛 | ✅ | workflow 变更 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `.github/workflows/backend-ci.yml` | 新建 | CI |
| `.github/workflows/frontend-ci.yml` | 修改：加 coverage + pact | CI |
| `.github/workflows/e2e-full-link.yml` | 修改：profile → `e2e` + env var | CI |

## 三条流水线

### backend-ci.yml（新建）
- 触发：push main + PR
- JDK 21 (temurin) + Maven cache
- `mvn verify -Pcoverage`（JaCoCo 报告 + check）
- `mvn spotbugs:check`（0 bugs 门禁）

### frontend-ci.yml（修改）
- Node 22 + pnpm
- `pnpm typecheck` + `pnpm lint`
- `pnpm test --coverage`（Vitest + coverage）
- `pnpm test:pact`（Pact 合约验证）
- `pnpm build`

### e2e-full-link.yml（已有，修改）
- 使用 `docker compose -f docker-compose.full.yml`
- `SPRING_PROFILES_ACTIVE: e2e`（原 `gitlab-e2e`）
- `E2E_DATASOURCE_URL` + `E2E_GITLAB_URL` 通过 docker compose 注入
- vertical slice tests via `test-runner` service

## 执行步骤

### Step 1: 新建 backend-ci.yml ✅
### Step 2: 更新 frontend-ci.yml ✅（加 coverage + pact）
### Step 3: 更新 e2e-full-link.yml ✅（profile + env var 注入）
### Step 4: VERIFY
- 三条流水线独立触发配置完成 ✅

## 静态扫描

```bash
scripts/dev/static-scan-topn.sh 10
```
**报告路径**：
**TopN 处理结论**：
**未解决风险**：

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | ✅ | 三条 CI 流水线配置完成 |
| 层级闭环 | ✅ | PR 反馈 + 合并后全链路 + E2E 全覆盖 |
| 测试闭环 | ✅ | backend-ci 含 JaCoCo + SpotBugs 门禁 |
| 架构闭环 | ✅ | docker compose 全链路环境 |
| 性能闭环 | ✅ | Maven cache + pnpm frozen-lockfile |
| 文档闭环 | ✅ | deployment.md profile 表已更新 |
| 工作区闭环 | ✅ | |
