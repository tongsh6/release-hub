# Slice 1: Profile 精简 + @ActiveProfiles 统一

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 1 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：待启动

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | Profile 精简，蓝图第一部分 |
| 用户价值 | ✅ | 消除 `unitTest`/`gitlab-e2e-local` 重复配置，profile 语义清晰 |
| 端到端路径 | ✅ | 贯穿层：Bootstrap config → Test annotations |
| 单一目标 | ✅ | 只做 profile 精简和注解统一 |
| 可独立验证 | ✅ | `grep -r "unitTest\|gitlab-e2e-local"` 无残留引用 |
| 可回滚 | ✅ | git revert，恢复删除的 YAML 文件和注解 |
| 依赖明确 | ✅ | 无前置依赖 |
| 风险收敛 | ✅ | 仅配置文件变更，不涉及业务逻辑 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `backend/.../resources/application-unitTest.yml` | 删除 | Bootstrap config |
| `backend/.../resources/application-gitlab-e2e-local.yml` | 删除 | Bootstrap config |
| `backend/.../resources/application-gitlab-e2e.yml` | 重写为 `application-e2e.yml`，env var 注入地址 | Bootstrap config |
| `backend/.../GroupApiTest.java` | 修改：`@ActiveProfiles("unitTest")` → `"test"` | Bootstrap test |
| `backend/.../WindowIterationPageApiTest.java` | 修改：`@ActiveProfiles("unitTest")` → `"test"` | Bootstrap test |
| `backend/.../ReleaseWindowPageApiTest.java` | 修改：`@ActiveProfiles("unitTest")` → `"test"` | Bootstrap test |
| `backend/.../GroupAppServiceIT.java` | 修改：`@ActiveProfiles("unitTest")` → `"test"` | Bootstrap test |
| `backend/.../AbstractGitLabE2ETest.java` | 修改：新增 `@ActiveProfiles("e2e")` | Bootstrap test |
| `docker-compose.full.yml` | 修改：`SPRING_PROFILES_ACTIVE: e2e`，env var 注入 | DevOps |

## 执行步骤

### Step 1: 删除 unitTest profile
- 删 `application-unitTest.yml`
- `GroupApiTest`, `WindowIterationPageApiTest`, `ReleaseWindowPageApiTest`, `GroupAppServiceIT` 改为 `@ActiveProfiles("test")`

### Step 2: 合并 gitlab-e2e → e2e
- 删 `application-gitlab-e2e-local.yml`
- 重写 `application-gitlab-e2e.yml` → `application-e2e.yml`（env var 注入）
- `AbstractGitLabE2ETest` 加 `@ActiveProfiles("e2e")`

### Step 3: Docker Compose 适配
- `docker-compose.full.yml`：`SPRING_PROFILES_ACTIVE: e2e`，注入 `E2E_DATASOURCE_URL` + `E2E_GITLAB_URL`

### Step 4: VERIFY
- `grep -r "unitTest" --include="*.java"` 无结果
- `grep -r "gitlab-e2e-local"` 无结果
- `grep -r "gitlab-e2e"` 无结果（除了 `e2e` 新文件自身）
- `mvn test` 全通过

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
