# Slice 7: CI 流水线重构

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 7 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：待启动

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
| `docker-compose.full.yml` | 修改（与 Slice 1 协调） | DevOps |

## 三条流水线

### backend-ci.yml（新建）
```yaml
name: Backend CI
on: { push: { branches: [main] }, pull_request: }
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - uses: actions/cache@v4
        with: { path: ~/.m2, key: maven-${{ hashFiles('**/pom.xml') }} }
      - run: cd backend && mvn verify -Pcoverage
      - run: cd backend && mvn spotbugs:check
```

### frontend-ci.yml（已有，修改）
- `pnpm test` → `pnpm test --coverage`
- 加 `pnpm test:pact`

### e2e-full-link.yml（已有，修改）
- `SPRING_PROFILES_ACTIVE: e2e`（原 `gitlab-e2e`）
- 注入 `E2E_DATASOURCE_URL=jdbc:postgresql://postgres:5432/releasehub`
- 注入 `E2E_GITLAB_URL=http://gitlab:80`

## 执行步骤

### Step 1: 新建 backend-ci.yml
### Step 2: 更新 frontend-ci.yml
### Step 3: 更新 e2e-full-link.yml（profile + env var）
### Step 4: VERIFY
- Push PR → backend-ci + frontend-ci 通过
- Merge → e2e-full-link 通过

## 静态扫描

```bash
scripts/dev/static-scan-topn.sh 10
```

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
