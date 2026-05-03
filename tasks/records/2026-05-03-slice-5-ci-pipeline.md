# Slice 5: CI 流水线重构

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 5 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：待启动

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | CI 流水线重构 |
| 用户价值 | ✅ | PR 阶段秒级单测反馈 + 合并后全链路验证 |
| 端到端路径 | ✅ | GitHub Actions → build → test → report |
| 单一目标 | ✅ | 拆分三条流水线，覆盖缺失的 Java 单测回归 |
| 可独立验证 | ✅ | GitHub Actions workflow run 通过 |
| 可回滚 | ✅ | git revert workflow 文件 |
| 依赖明确 | ✅ | 依赖 Slice 2（Maven phase 分离）+ Slice 4（Playwright 迁移完成，CI 脚本可更新） |
| 风险收敛 | ✅ | 新增 workflow 不影响已有流程 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `.github/workflows/backend-ci.yml` | 新建 | CI |
| `.github/workflows/frontend-ci.yml` | 修改：测试脚本更新 | CI |
| `.github/workflows/e2e-full-link.yml` | 修改：profile 引用 + env var | CI |
| `docker-compose.full.yml` | 修改：SPRING_PROFILES_ACTIVE: e2e + env var（与 Slice 1 协调） | DevOps |

## 三条流水线

### backend-ci.yml（新建）
```yaml
name: Backend CI
on:
  push:
    branches: [main]
  pull_request:
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - name: Unit tests
        run: cd backend && mvn test
      - name: SpotBugs
        run: cd backend && mvn spotbugs:check
```

### frontend-ci.yml（已有，修改测试脚本）
- `pnpm test` 已在 scripts 中，Vitest 补齐后自然生效
- 新增 `pnpm test:e2e:smoke` 或维持现状（E2E 太慢，PR 阶段不跑）

### e2e-full-link.yml（已有，修改 profile + env var）
- `SPRING_PROFILES_ACTIVE: e2e`
- 注入 `E2E_DATASOURCE_URL`、`E2E_GITLAB_URL`
- 继续用 `docker compose -f docker-compose.full.yml`

## 执行步骤

### Step 1: 新建 backend-ci.yml
- Java 21 + Maven 缓存
- `mvn test`（surefire 单测）
- `mvn spotbugs:check`（已有 Plugin）

### Step 2: 更新 e2e-full-link.yml
- profile 从 `gitlab-e2e` → `e2e`
- 注入 env var 地址

### Step 3: 更新 frontend-ci.yml
- 确认 `pnpm test` 步骤正确

### Step 4: VERIFY
- `gh workflow run backend-ci` 或 push PR 触发
- 三条 workflow 均正常

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | ⬜ | |
| 层级闭环 | ⬜ | |
| 测试闭环 | ⬜ | |
| 架构闭环 | ⬜ | |
| 性能闭环 | ⬜ | |
| 文档闭环 | ⬜ | |
| 工作区闭环 | ⬜ | |

## 静态扫描

**扫描命令**：
**报告路径**：
**TopN 处理结论**：
**未解决风险**：

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| | | |

## 经验沉淀

- [ ] 不需要
- [ ] 已创建经验文档
- [ ] 已更新经验索引
