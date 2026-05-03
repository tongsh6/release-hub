# Slice 1: Profile 精简 + @ActiveProfiles 统一

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 1 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：进行中

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | Profile 精简，蓝图第一部分 |
| 用户价值 | ✅ | 消除 4 个重复/混淆 profile，统一为 test + e2e |
| 端到端路径 | ✅ | Bootstrap config → Test annotation → Docker Compose |
| 单一目标 | ✅ | 只做 YAML 删除/重写和注解修改 |
| 可独立验证 | ✅ | `grep -r "unitTest\|gitlab-e2e-local\|gitlab-e2e"` 无残留，`mvn test` 通过 |
| 可回滚 | ✅ | git revert |
| 依赖明确 | ✅ | 无前置依赖 |
| 风险收敛 | ✅ | 纯配置文件/注解，不涉及业务逻辑 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `application-unitTest.yml` | 删除 | Bootstrap config |
| `application-gitlab-e2e-local.yml` | 删除 | Bootstrap config |
| `application-gitlab-e2e.yml` | 重写为 `application-e2e.yml` | Bootstrap config |
| `GroupApiTest.java` | 修改：`@ActiveProfiles("unitTest")` → `"test"` | Bootstrap test |
| `WindowIterationPageApiTest.java` | 修改：同上 | Bootstrap test |
| `ReleaseWindowPageApiTest.java` | 修改：同上 | Bootstrap test |
| `GroupAppServiceIT.java` | 修改：同上 | Bootstrap test |
| `AbstractGitLabE2ETest.java` | 修改：加 `@ActiveProfiles("e2e")` | Bootstrap test |
| `docker-compose.full.yml` | 修改：`SPRING_PROFILES_ACTIVE: e2e` + env var | DevOps |

## 执行步骤

### Step 1: 删除 unitTest
- 删 `application-unitTest.yml`
- 4 个 Java 文件 `@ActiveProfiles("unitTest")` → `"test"`

### Step 2: 合并 gitlab-e2e → e2e
- 删 `application-gitlab-e2e-local.yml`
- `application-gitlab-e2e.yml` → `application-e2e.yml`（env var 注入地址）
- `AbstractGitLabE2ETest` 加 `@ActiveProfiles("e2e")`

### Step 3: Docker Compose 适配
- `docker-compose.full.yml`：`SPRING_PROFILES_ACTIVE: e2e` + 注入 `E2E_DATASOURCE_URL`/`E2E_GITLAB_URL`

### Step 4: VERIFY
- `grep -r "unitTest" --include="*.java" --include="*.yml"` 无结果
- `grep -r "application-gitlab-e2e\|gitlab-e2e-local" --include="*.java" --include="*.yml" --include="*.yaml"` 无结果
- `mvn test` 全通过

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
| 行为闭环 | ⬜ | |
| 层级闭环 | ⬜ | |
| 测试闭环 | ⬜ | |
| 架构闭环 | ⬜ | |
| 性能闭环 | ⬜ | |
| 文档闭环 | ⬜ | |
| 工作区闭环 | ⬜ | |
