# ReleaseHub 测试体系重构设计

**日期**: 2026-05-03 | **状态**: Proposed

## 一、目标

梳理并重构 ReleaseHub 全栈测试体系，建立测试金字塔分层、统一测试 profile、规范 CI 流水线，消除当前测试体系的零散和重复。

## 二、Profile 精简

测试相关 profile 精简为两个：

| Profile | 数据库 | GitLab | 场景 |
|---------|--------|--------|------|
| `test` | H2 内存 | Mock | 快速单测、API 集成测试 |
| `e2e` | 真实 PG | 真实 GitLab | 全链路 E2E |

`local`、`prd` 保持不动。

### 2.1 `e2e` Profile 的两种模式

`e2e` 的 PG 和 GitLab 地址通过环境变量注入：

```yaml
# application-e2e.yml
spring:
  datasource:
    url: ${E2E_DATASOURCE_URL:jdbc:postgresql://localhost:5433/release_hub}
    username: ${E2E_DATASOURCE_USERNAME:postgres}
    password: ${E2E_DATASOURCE_PASSWORD:123456}

releasehub:
  gitlab:
    real-adapter: true
    base-url: ${E2E_GITLAB_URL:http://localhost:9080}
  seed:
    enabled: true
```

| | Mode A（本机常驻） | Mode B（CI Docker Compose） |
|------|------|------|
| **PG 生命周期** | 提前 `docker compose up -d`，常驻 | `docker compose up`，跑完 `down -v` |
| **GitLab 生命周期** | 同上，数据持久化 | 同上，销毁 |
| **PG 地址** | localhost:5433（默认值） | postgres:5432（容器内服务名） |
| **GitLab 地址** | localhost:9080（默认值） | http://gitlab:80（容器内服务名） |
| **Backend** | 宿主机 `mvn spring-boot:run` 或 IDE | Dockerfile 构建镜像 |
| **Frontend** | 宿主机 `pnpm dev` | Dockerfile 构建 + nginx |
| **Host 端口** | PG:5433, GitLab:9080, Backend:8080, Frontend:5173 | PG:5432, GitLab:9081, Backend:8081, Frontend:8090 |

两种模式可在同一台机器并行运行，端口互不冲突。

### 2.2 文件变更

| 操作 | 文件 |
|------|------|
| 删除 | `application-unitTest.yml`（与 test 完全重复） |
| 删除 | `application-gitlab-e2e-local.yml`（合并进 e2e） |
| 重写 | `application-gitlab-e2e.yml` → `application-e2e.yml`（env var 覆盖地址） |
| 不改 | `application-test.yml`、`application-local.yml`、`application-prd.yml` |

### 2.3 @ActiveProfiles 统一

| 文件 | 变更 |
|------|------|
| `GroupApiTest.java` | `"unitTest"` → `"test"` |
| `WindowIterationPageApiTest.java` | `"unitTest"` → `"test"` |
| `ReleaseWindowPageApiTest.java` | `"unitTest"` → `"test"` |
| `GroupAppServiceIT.java` | `"unitTest"` → `"test"` |
| `AbstractGitLabE2ETest.java` | 新增 `@ActiveProfiles("e2e")` |

## 三、Maven Phase 分离

引入 `maven-failsafe-plugin`，将测试按是否需要 Spring 上下文分离：

- **surefire（`mvn test`）**：Domain/Application/Infrastructure 单测 + ArchUnit，不加载 Spring，秒级
- **failsafe（`mvn verify`）**：API 集成测试 + IT + E2E，加载 Spring 上下文，分钟级

### 3.1 命名约定

| 后缀 | 插件 | 说明 |
|------|------|------|
| `*Test.java` | surefire | 纯 JUnit，不加载 Spring |
| `*ApiTest.java` | failsafe | Controller 集成测试 |
| `*IT.java` | failsafe | 跨模块集成测试 |
| `*E2eTest.java` | failsafe | 全链路 E2E |

### 3.2 日常命令

```bash
mvn test              # 快速单测，< 30s
mvn verify            # 单测 + 集成 + E2E，提交前/CI
mvn verify -DskipITs  # 跳过集成测试
```

## 四、前端测试体系

### 4.1 Vitest 单测

为 composables、stores、api 层补齐 Vitest 单元测试，组件测试覆盖关键业务组件。

### 4.2 Puppeteer → Playwright

将自建 Puppeteer E2E 框架迁移到 Playwright：

- 删除自建 `TestRunner`、`PageHelper`、`Assertions`（~600 行基础设施）
- 利用 Playwright 内置断言自动等待、Trace Viewer、并行执行
- 逐文件迁移 9 个 E2E 文件

### 4.3 最终脚本

```json
{
  "test": "vitest run",
  "test:e2e": "playwright test",
  "test:e2e:ui": "playwright test --ui"
}
```

## 五、CI 测试流水线

拆分为三条流水线：

| 流水线 | 触发 | 内容 | 耗时 |
|--------|------|------|------|
| `backend-ci.yml` | PR / push main | `mvn test` (surefire) | < 2min |
| `frontend-ci.yml` | PR / push main | typecheck + lint + vitest + build | < 2min |
| `e2e-full-link.yml` | push main / 手动 | docker compose up Mode B 全链路 | ~15min |

关键变更：
- **新建 `backend-ci.yml`** — Java 单测回归目前 CI 缺失
- **`e2e-full-link.yml` 适配合并后 profile** — `SPRING_PROFILES_ACTIVE: e2e`
- **`frontend-ci.yml`** 已有，Vitest 补齐后自然覆盖

## 六、不包含

- 代码覆盖率工具（JaCoCo）— 后续单独引入
- 性能/负载测试
- 合约测试
- 生产环境部署
