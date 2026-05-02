# ReleaseHub 全链路 E2E 测试环境设计

**日期**: 2026-05-02 | **状态**: Proposed

## 一、目标

建立一个 `docker compose up` 一键启动的全栈环境，自动运行以垂直场景链路为单位的 E2E 测试，覆盖全部 11 个用户故事。

环境包括：PostgreSQL + GitLab CE + Spring Boot Backend + Vue Frontend + Nginx + Test Runner。

## 二、两种运行模式

### 模式 A：本机常驻（开发调试）

PostgreSQL 和 GitLab 常驻本机，数据持久化，方便查看数据库表、仓库变化记录。每次测试只启动 backend + frontend，秒级开始。

```
本机常驻（手动启动一次，一直运行）：
├── PostgreSQL (localhost:5433)  ← docker compose up -d（已有 docs/docker-compose.yml）
└── GitLab CE  (localhost:9080)  ← docker compose up -d（新 docker-compose.gitlab.yml）

每次测试（秒级启动）：
├── backend  (localhost:8080)    ← mvn spring-boot:run -Dprofile=gitlab-e2e-local
├── frontend (localhost:5173)    ← pnpm dev
└── test-runner                  ← scripts/e2e/run-vertical-slices.sh
```

### 模式 B：CI 全量 docker compose（一次销毁）

所有服务从零启动，跑完自动销毁，不留痕迹。

```
docker compose -f docker-compose.full.yml up
    ├── postgres    ← 新容器
    ├── gitlab      ← 新容器
    ├── backend     ← 新容器
    ├── frontend    ← 新容器
    └── test-runner ← depends_on all，跑完退出
docker compose -f docker-compose.full.yml down -v
```

### 两种模式的对应关系

| | 模式 A：本机常驻 | 模式 B：CI 全量 |
|---|---|---|
| **PostgreSQL** | 本机 docs/docker-compose.yml，常驻 | docker-compose.full.yml 内，随启随停 |
| **GitLab** | 本机新 docker-compose.gitlab.yml，常驻 | docker-compose.full.yml 内，随启随停 |
| **Backend** | `mvn spring-boot:run` 或 jar，profile=gitlab-e2e-local | Dockerfile 构建，profile=gitlab-e2e |
| **Frontend** | `pnpm dev` | Dockerfile 构建 + nginx |
| **数据持久化** | ✓ 数据库和仓库数据保留，方便排查 | ✗ 销毁 |
| **适用场景** | 本地开发调试、功能验证 | CI 自动化、PR 门禁 |

### 配置文件

| 文件 | 对应模式 | 说明 |
|------|---------|------|
| `docs/docker-compose.yml` | A | 已有，仅 PostgreSQL |
| `docker-compose.gitlab.yml` | A | 新增，GitLab CE 独立部署 |
| `docker-compose.full.yml` | B | 新增，全栈编排（postgres + gitlab + backend + frontend + test-runner） |
| `application-gitlab-e2e-local.yml` | A | 连 localhost:5433 + localhost:9080 |
| `application-gitlab-e2e.yml` | B | 连容器名 postgres:5432 + gitlab:80 |

## 三、Docker Compose 编排（模式 B）

| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| `postgres` | `postgres:18.1` | 5432 | 已有 |
| `gitlab` | `gitlab/gitlab-ce:17.11.3-ce.0` | 9080 (host) → 80 (container) | 新增 |
| `backend` | `eclipse-temurin:21-jre-alpine`（自定义 Dockerfile） | 8080 | 新增 |
| `frontend` | `node:24-alpine`（`pnpm dev`） | 5173 | 新增 |
| `test-runner` | `node:24-alpine` | — | 新增，运行完退出 |

### 3.1 关键配置

- **gitlab**：通过 `GITLAB_OMNIBUS_CONFIG` 禁用 Prometheus、Grafana、Alertmanager 以降低内存（目标 < 2GB）。预设 root 密码和 personal access token
- **backend**：使用新 profile `gitlab-e2e`，连接 postgres + gitlab 容器
- **frontend**：`VITE_API_BASE_URL=http://backend:8080`
- **test-runner**：`depends_on` 所有服务 healthy 后启动，执行测试后退出

### 3.2 新增文件

| 文件 | 说明 |
|------|------|
| `docker-compose.full.yml` | 全栈编排 |
| `backend/Dockerfile` | 后端多阶段构建 |
| `frontend/Dockerfile` | 前端构建 + nginx |
| `frontend/nginx.conf` | 前端静态 + API 反向代理 |
| `scripts/e2e/init-gitlab.sh` | GitLab 初始化：创建 token + 种子仓库 |
| `scripts/e2e/run-vertical-slices.sh` | 垂直切片测试编排入口 |
| `backend/.../application-gitlab-e2e.yml` | 新 Spring profile |

## 四、GitLab 初始化脚本

### 4.1 流程

```
1. 等待 GitLab health check 通过
2. 通过 root token 创建 test user（admin/admin）
3. 创建 personal access token
4. 创建 3 个种子仓库（含 pom.xml / gradle.properties）
5. 配置 webhook / CI（可选，用于 triggerPipeline 验证）
```

### 4.2 种子仓库结构

每个种子仓库是一个最小化的 Java/Gradle 项目，包含版本号文件供 VersionExtractor 解析：

```
seed-repo-1/   (Maven 单模块)
├── pom.xml    # <version>1.4.0</version>
├── src/main/java/App.java

seed-repo-2/   (Maven 多模块)
├── pom.xml    # <version>2.1.0</version>, <modules><module>lib</module></modules>
├── lib/pom.xml  # <parent><version>2.1.0</version></parent>
├── src/main/java/App.java

seed-repo-3/   (Gradle)
├── gradle.properties  # version=3.0.0
├── build.gradle
├── src/main/java/App.java
```

三个仓库通过 GitLab API 创建，每个仓库初始化 commit 包含上述文件，默认分支为 `main`。

## 五、两个新 Spring Profile

### 5.1 gitlab-e2e-local（模式 A：连本机常驻服务）

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/release_hub
    username: postgres
    password: 123456
  flyway:
    enabled: true
  jpa:
    hibernate.ddl-auto: validate

releasehub:
  gitlab:
    base-url: http://localhost:9080
    real-adapter: true
  seed:
    enabled: true

security.jwt.secret: gitlab-e2e-local-test-secret
```

### 5.2 gitlab-e2e（模式 B：连容器服务）

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/releasehub
  flyway:
    enabled: true

releasehub:
  gitlab:
    base-url: http://gitlab:80
    real-adapter: true

security.jwt.secret: gitlab-e2e-test-secret
```

## 六、垂直切片场景设计

6 条垂直场景链路，覆盖全部 11 个用户故事。

### Slice 1: 发布窗口完整生命周期（US-RW）

```
创建窗口(DRAFT) → 配置时间窗口 → 验证状态 → 冻结 → 解冻 → 发布(PUBLISHED) → 关闭(CLOSED)
                                                                      ↓
                                                              验证：状态机每步流转正确
                                                              验证：frozen=true/false 正确
                                                              验证：publishedAt 已设置
                                                              验证：关闭后状态不可逆
```

### Slice 2: 迭代仓库关联 + 版本号推导（US-IT + US-REPO）

```
创建仓库(真实 GitLab) → 从仓库提取版本号(1.4.0)
                      → 创建迭代 + 关联仓库
                      → 验证 feature 分支已在 GitLab 创建（feature/ITER-xxx）
                      → 验证 dev_version = 1.5.0-SNAPSHOT（MINOR+1）
                      → 验证 base_version = 1.4.0, target_version = 1.5.0
```

### Slice 3: 发布执行编排（US-REL）

```
创建窗口 → 挂载迭代 → 冻结 → 发布
                              ↓
验证：release 分支已在 GitLab 创建（release/RW-xxx）
验证：feature 分支已 merge 到 release
验证：MR/PR 已创建并合并
验证：Run 记录包含 ENSURE_FEATURE → ENSURE_RELEASE → ENSURE_MR → TRY_MERGE
验证：branch-status 返回 mergeStatus=MERGED
```

### Slice 4: 发布收尾清理（US-POST）

```
Slice 3 的窗口 → 关闭
                   ↓
验证：迭代已关闭
验证：版本号已推导（移除 -SNAPSHOT）
验证：feature 分支已归档（archive/released/feature/ITER-xxx）
验证：release 分支已 merge 到 master/main
验证：tag v1.5.0 已在 GitLab 创建
验证：CI pipeline 已触发
验证：Run 记录包含 CLOSE_ITERATION → UPDATE_VERSION → ARCHIVE_BRANCH → MERGE_TO_MASTER → CREATE_TAG → TRIGGER_CI
```

### Slice 5: 冲突检测 + 阻断（US-VAL）

```
创建窗口 → 挂载迭代 → 变更仓库版本号（手动修改 pom.xml）
                      → 同步版本号
                      → 验证：系统检测到版本冲突（REPO_AHEAD）
                      → 提供解决选项：系统版本 / 仓库版本 / 取消
                      → 选择"使用系统版本"
                      → 验证：仓库文件已回退为系统版本

合并冲突场景：
窗口发布前，在 GitLab 中手动创建一个冲突 MR
                      → 发布
                      → 验证：checkMergeability 返回 conflict
                      → 验证：Run 状态为 FAILED
                      → 手动解决冲突 → 重试
                      → 验证：Run 状态变为 SUCCESS
```

### Slice 6: 分支规则 + 分组管理（US-BR + US-GROUP + US-SET）

```
创建分组树 → 子分组挂载仓库 → 验证叶子节点约束
创建分支规则(TEMPLATE: feature/*) → 创建不合规分支 → 验证被拦截
创建分支规则(REGEX: ^feature/[A-Z]+-\d+$) → test API 验证匹配/不匹配
系统设置：保存 GitLab 配置 → 验证连接成功
系统设置：保存命名模板 → 验证模板生效
```

## 七、Test Runner 编排

```bash
#!/bin/bash
# scripts/e2e/run-vertical-slices.sh

echo "=== 等待所有服务就绪 ==="
wait-for-it backend:8080 -t 120
wait-for-it frontend:5173 -t 60
wait-for-it gitlab:80 -t 180

echo "=== 初始化 GitLab ==="
./scripts/e2e/init-gitlab.sh

echo "=== Slice 1: 发布窗口生命周期 ==="
cd backend && mvn test -Dtest=Slice1_RW_Lifecycle -Dprofile=gitlab-e2e

echo "=== Slice 2: 迭代仓库版本号 ==="
cd backend && mvn test -Dtest=Slice2_IT_Repo_Version -Dprofile=gitlab-e2e

# ... Slice 3-6

echo "=== 前端业务流验证 ==="
cd frontend && npx tsx e2e/tests/business-flow-e2e.test.ts

echo "=== 报告汇总 ==="
# 汇总 JUnit XML + 前端 E2E 结果，输出一份报告
```

## 八、CI 集成（GitHub Actions）

```yaml
# .github/workflows/e2e-full-link.yml
name: Full-Link E2E Tests
on:
  push:
    branches: [main]
  pull_request:

jobs:
  e2e:
    runs-on: ubuntu-latest  # 需要 8GB+ RAM (GitLab)
    steps:
      - uses: actions/checkout@v4
      - name: Start full stack
        run: docker compose -f docker-compose.full.yml up -d
      - name: Run vertical slice tests
        run: docker compose -f docker-compose.full.yml run test-runner
      - name: Collect reports
        if: always()
        run: |
          docker compose -f docker-compose.full.yml logs > e2e-logs.txt
      - name: Teardown
        if: always()
        run: docker compose -f docker-compose.full.yml down -v
```

## 九、不包含

- 生产环境部署（本设计仅覆盖测试环境）
- 性能/负载测试
- K8s 编排
- GitHub/Gitea 的额外适配器
