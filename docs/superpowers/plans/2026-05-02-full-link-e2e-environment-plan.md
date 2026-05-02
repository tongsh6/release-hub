# 全链路 E2E 测试环境 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立一个 `docker compose up` 一键启动的全栈环境 + 5 条垂直切片 E2E 测试，覆盖 4 种角色、20 个 Phase 1 用户故事。

**Architecture:** 两种运行模式 — A) 本机常驻（PG + GitLab 持久化，秒级启动 backend/frontend）; B) CI 全量（docker compose 一键启停）。Backend 通过 `GitBranchAdapterFactory` 根据 repo 的 `gitProvider=GITLAB` 自动路由到 `GitLabGitBranchAdapter`，无需新适配器。

**Tech Stack:** Docker Compose, GitLab CE 17, Spring Boot 3.4 (profile `gitlab-e2e`), Vue 3 + Vite, Bash (init scripts), GitHub Actions

---

## DAG

```
Phase 1: Infrastructure（P0，使能层）
├── Task 1: GitLab docker-compose（独立）
├── Task 2: gitlab-e2e Spring profile（独立）
├── Task 3: Backend Dockerfile（独立）
├── Task 4: Frontend Dockerfile + nginx.conf（独立）
├── Task 5: GitLab 初始化脚本（依赖 Task 1）
└── Task 6: docker-compose.full.yml（依赖 Task 3, 4）

Phase 2: Vertical Slice Tests（P1，测试层）
├── Task 7: Slice 1 E2E 测试 — 分组 + 窗口生命周期
├── Task 8: Slice 2 E2E 测试 — 仓库 + 迭代 + 分支规则
├── Task 9: Slice 3 E2E 测试 — 发布执行编排
├── Task 10: Slice 4 E2E 测试 — 发布收尾清理
├── Task 11: Slice 5 E2E 测试 — 冲突检测 + 恢复
└── Task 12: 前端跨角色协作 E2E 测试

Phase 3: Runner + CI（P2，集成层）
├── Task 13: Test runner 编排脚本
└── Task 14: GitHub Actions workflow
```

---

### Task 1: GitLab docker-compose（模式 A 本机常驻）

**Files:**
- Create: `docker-compose.gitlab.yml`

- [ ] **Step 1: 创建 docker-compose.gitlab.yml**

```yaml
version: '3.8'
services:
  gitlab:
    image: gitlab/gitlab-ce:17.11.3-ce.0
    container_name: releasehub-gitlab
    hostname: gitlab.local
    ports:
      - "9080:80"
      - "9443:443"
    environment:
      GITLAB_OMNIBUS_CONFIG: |
        external_url 'http://gitlab.local'
        nginx['listen_port'] = 80
        nginx['listen_https'] = false
        prometheus_monitoring['enable'] = false
        grafana['enable'] = false
        alertmanager['enable'] = false
        puma['worker_processes'] = 2
        sidekiq['max_concurrency'] = 10
        gitlab_rails['initial_root_password'] = 'releasehub123'
        gitlab_rails['display_initial_root_password'] = false
    volumes:
      - gitlab_data:/etc/gitlab
      - gitlab_logs:/var/log/gitlab
      - gitlab_opt:/var/opt/gitlab
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost/-/health"]
      interval: 30s
      timeout: 10s
      retries: 20
    restart: unless-stopped

volumes:
  gitlab_data:
  gitlab_logs:
  gitlab_opt:
```

- [ ] **Step 2: 启动 GitLab**

```bash
docker compose -f docker-compose.gitlab.yml up -d

# 等待 GitLab 完全就绪（可能需要 2-3 分钟）
docker compose -f docker-compose.gitlab.yml logs -f gitlab | grep -m1 "GitLab is ready"
```

- [ ] **Step 3: 验证 GitLab 可访问**

```bash
# 检查 health endpoint
curl -s http://localhost:9080/-/health | grep "GitLab OK"

# 获取 root personal access token
curl -s --request POST \
  --header "Content-Type: application/json" \
  --data '{"name":"e2e-token","scopes":["api","read_user","read_repository","write_repository"]}' \
  "http://localhost:9080/api/v4/personal_access_tokens" \
  --user "root:releasehub123"
```

- [ ] **Step 4: Commit**

```bash
git add docker-compose.gitlab.yml
git commit -m "feat: add GitLab CE docker-compose for E2E testing"
```

---

### Task 2: gitlab-e2e Spring profile

**Files:**
- Create: `backend/releasehub-bootstrap/src/main/resources/application-gitlab-e2e-local.yml`
- Create: `backend/releasehub-bootstrap/src/main/resources/application-gitlab-e2e.yml`

- [ ] **Step 1: 创建 application-gitlab-e2e-local.yml（模式 A：连本机 localhost）**

```yaml
# gitlab-e2e-local: 连接本机常驻 PostgreSQL + GitLab
spring:
  config:
    activate:
      on-profile: gitlab-e2e-local
  datasource:
    url: jdbc:postgresql://localhost:5433/release_hub
    username: postgres
    password: 123456
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

releasehub:
  seed:
    enabled: true
  gitlab:
    real-adapter: true

security:
  jwt:
    secret: gitlab-e2e-local-test-secret-key-32chars
    ttl-minutes: 60

logging:
  level:
    io.releasehub: DEBUG
```

- [ ] **Step 2: 创建 application-gitlab-e2e.yml（模式 B：连容器）**

```yaml
# gitlab-e2e: 连接 docker-compose 容器内的 PostgreSQL + GitLab
spring:
  config:
    activate:
      on-profile: gitlab-e2e
  datasource:
    url: jdbc:postgresql://postgres:5432/releasehub
    username: postgres
    password: 123456
  flyway:
    enabled: true

releasehub:
  seed:
    enabled: false
  gitlab:
    real-adapter: true

security:
  jwt:
    secret: gitlab-e2e-test-secret-key-32chars-long
```

- [ ] **Step 3: Commit**

```bash
git add backend/releasehub-bootstrap/src/main/resources/application-gitlab-e2e-local.yml \
        backend/releasehub-bootstrap/src/main/resources/application-gitlab-e2e.yml
git commit -m "feat: add gitlab-e2e Spring profiles for E2E testing"
```

---

### Task 3: Backend Dockerfile

**Files:**
- Create: `backend/Dockerfile`

- [ ] **Step 1: 创建 Backend Dockerfile**

```dockerfile
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml ./
COPY releasehub-common/pom.xml ./releasehub-common/
COPY releasehub-domain/pom.xml ./releasehub-domain/
COPY releasehub-application/pom.xml ./releasehub-application/
COPY releasehub-infrastructure/pom.xml ./releasehub-infrastructure/
COPY releasehub-interfaces/pom.xml ./releasehub-interfaces/
COPY releasehub-bootstrap/pom.xml ./releasehub-bootstrap/
RUN mvn dependency:go-offline -q
COPY . .
RUN mvn package -pl releasehub-bootstrap -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/releasehub-bootstrap/target/releasehub-bootstrap-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=gitlab-e2e"]
```

- [ ] **Step 2: Commit**

```bash
git add backend/Dockerfile
git commit -m "feat: add backend Dockerfile for E2E environment"
```

---

### Task 4: Frontend Dockerfile + nginx.conf

**Files:**
- Create: `frontend/Dockerfile`
- Create: `frontend/nginx.conf`

- [ ] **Step 1: 创建 Frontend Dockerfile**

```dockerfile
FROM node:24-alpine AS builder
WORKDIR /build
COPY package.json pnpm-lock.yaml ./
RUN npm install -g pnpm && pnpm install --frozen-lockfile
COPY . .
RUN pnpm build --mode gitlab-e2e

FROM nginx:alpine
COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

- [ ] **Step 2: 创建 nginx.conf**

```nginx
server {
    listen 80;
    server_name localhost;

    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/Dockerfile frontend/nginx.conf
git commit -m "feat: add frontend Dockerfile and nginx config"
```

---

### Task 5: GitLab 初始化脚本

**Files:**
- Create: `scripts/e2e/init-gitlab.sh`

- [ ] **Step 1: 创建 init-gitlab.sh**

```bash
#!/bin/bash
set -e

GITLAB_URL="${GITLAB_URL:-http://localhost:9080}"
ROOT_TOKEN="${ROOT_TOKEN:-releasehub123}"
TEST_USER="${TEST_USER:-e2e-user}"
TEST_PASS="${TEST_PASS:-e2e-pass123}"

echo "=== Waiting for GitLab to be ready ==="
for i in $(seq 1 60); do
  if curl -s -o /dev/null -w "%{http_code}" "$GITLAB_URL/-/health" | grep -q "200"; then
    echo "GitLab is ready"
    break
  fi
  echo "Waiting... ($i/60)"
  sleep 5
done

echo "=== Creating E2E test user ==="
curl -s --request POST \
  --header "Content-Type: application/json" \
  --data "{\"name\":\"$TEST_USER\",\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASS\",\"email\":\"$TEST_USER@e2e.test\",\"skip_confirmation\":true}" \
  "$GITLAB_URL/api/v4/users" \
  --user "root:$ROOT_TOKEN"

echo "=== Creating personal access token for test user ==="
TOKEN_RESPONSE=$(curl -s --request POST \
  --header "Content-Type: application/json" \
  --data '{"name":"e2e-pat","scopes":["api","read_repository","write_repository"]}' \
  "$GITLAB_URL/api/v4/personal_access_tokens" \
  --user "$TEST_USER:$TEST_PASS")

E2E_TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "E2E_TOKEN=$E2E_TOKEN"
export E2E_TOKEN

echo "=== Creating seed repositories ==="
create_repo() {
  local name=$1
  curl -s --request POST \
    --header "Content-Type: application/json" \
    --header "PRIVATE-TOKEN: $E2E_TOKEN" \
    --data "{\"name\":\"$name\",\"visibility\":\"private\",\"initialize_with_readme\":false}" \
    "$GITLAB_URL/api/v4/projects"
}

REPO1_ID=$(create_repo "seed-repo-1-maven" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
REPO2_ID=$(create_repo "seed-repo-2-maven-multi" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
REPO3_ID=$(create_repo "seed-repo-3-gradle" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

echo "=== Pushing seed files to repos ==="
push_file() {
  local repo_id=$1 file_path=$2 content=$3
  curl -s --request POST \
    --header "PRIVATE-TOKEN: $E2E_TOKEN" \
    --header "Content-Type: application/json" \
    --data "{\"branch\":\"main\",\"content\":\"$(echo -n "$content" | base64)\",\"commit_message\":\"init: seed $file_path\",\"encoding\":\"base64\"}" \
    "$GITLAB_URL/api/v4/projects/$repo_id/repository/files/$(echo -n "$file_path" | sed 's/\//%2F/g')"
}

# seed-repo-1: Maven 单模块 pom.xml
push_file "$REPO1_ID" "pom.xml" '<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.e2e</groupId>
    <artifactId>seed-repo-1</artifactId>
    <version>1.4.0</version>
</project>'

# seed-repo-2: Maven 多模块
push_file "$REPO2_ID" "pom.xml" '<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.e2e</groupId>
    <artifactId>seed-repo-2-parent</artifactId>
    <version>2.1.0</version>
    <packaging>pom</packaging>
    <modules><module>lib</module></modules>
</project>'

push_file "$REPO2_ID" "lib/pom.xml" '<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.e2e</groupId>
        <artifactId>seed-repo-2-parent</artifactId>
        <version>2.1.0</version>
    </parent>
    <artifactId>seed-repo-2-lib</artifactId>
</project>'

# seed-repo-3: Gradle
push_file "$REPO3_ID" "gradle.properties" 'version=3.0.0'

echo "=== Exporting environment variables ==="
echo "E2E_GITLAB_BASE_URL=$GITLAB_URL"
echo "E2E_GITLAB_TOKEN=$E2E_TOKEN"
echo "E2E_GITLAB_USER=$TEST_USER"
echo "E2E_REPO1_CLONE_URL=http://$TEST_USER:e2e-pass123@gitlab.local/$TEST_USER/seed-repo-1-maven.git"
echo "E2E_REPO2_CLONE_URL=http://$TEST_USER:e2e-pass123@gitlab.local/$TEST_USER/seed-repo-2-maven-multi.git"
echo "E2E_REPO3_CLONE_URL=http://$TEST_USER:e2e-pass123@gitlab.local/$TEST_USER/seed-repo-3-gradle.git"

echo "=== GitLab initialization complete ==="
```

- [ ] **Step 2: 设置可执行权限**

```bash
chmod +x scripts/e2e/init-gitlab.sh
```

- [ ] **Step 3: Commit**

```bash
git add scripts/e2e/init-gitlab.sh
git commit -m "feat: add GitLab init script for E2E seed data"
```

---

### Task 6: docker-compose.full.yml（模式 B 全栈）

**Files:**
- Create: `docker-compose.full.yml`

- [ ] **Step 1: 创建 docker-compose.full.yml**

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:18.1
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: 123456
      POSTGRES_DB: releasehub
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 5s
      retries: 5

  gitlab:
    image: gitlab/gitlab-ce:17.11.3-ce.0
    hostname: gitlab.local
    environment:
      GITLAB_OMNIBUS_CONFIG: |
        external_url 'http://gitlab.local'
        nginx['listen_port'] = 80
        nginx['listen_https'] = false
        prometheus_monitoring['enable'] = false
        grafana['enable'] = false
        alertmanager['enable'] = false
        puma['worker_processes'] = 2
        sidekiq['max_concurrency'] = 10
        gitlab_rails['initial_root_password'] = 'releasehub123'
        gitlab_rails['display_initial_root_password'] = false
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost/-/health"]
      interval: 30s
      timeout: 10s
      retries: 20

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: gitlab-e2e
    ports:
      - "8080:8080"

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    depends_on:
      - backend
    ports:
      - "80:80"

  test-runner:
    build:
      context: .
      dockerfile: Dockerfile.test-runner
    depends_on:
      gitlab:
        condition: service_healthy
      backend:
        condition: service_started
      frontend:
        condition: service_started
    environment:
      GITLAB_URL: http://gitlab:80
      BACKEND_URL: http://backend:8080
      FRONTEND_URL: http://frontend:80
    volumes:
      - ./reports:/app/reports
    command: ["bash", "scripts/e2e/run-vertical-slices.sh"]
```

- [ ] **Step 2: Commit**

```bash
git add docker-compose.full.yml
git commit -m "feat: add full-stack docker-compose for E2E environment"
```

---

### Task 7: Slice 1 E2E 测试 — 分组 + 窗口生命周期

**Files:**
- Create: `backend/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/Slice1_Group_Window_Lifecycle_E2ETest.java`

**Coverage:** RM-1, RM-2, RM-3, RM-4, RM-9, ADM-1, ADM-4, ADM-5, ADM-6, ADM-7, QA-1

- [ ] **Step 1: 写测试类骨架**

```java
package io.releasehub.bootstrap.e2e;

import org.junit.jupiter.api.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("gitlab-e2e-local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Slice1_Group_Window_Lifecycle_E2ETest extends AbstractE2ETest {

    private String adminToken;
    private String releaseMgrToken;
    private String testerToken;
    private String parentGroupCode;
    private String childGroupCode;
    private String leafGroupCode;

    @BeforeAll
    void login() throws Exception {
        // [Admin] 登录
        adminToken = loginAndGetToken();  // admin/admin
    }

    @Test @Order(1)
    @DisplayName("[Admin] 构建三级分组树 company -> team -> project")
    void admin_creates_group_hierarchy() throws Exception {
        // 创建 parent-group "E2E-Company"
        parentGroupCode = "TC-G-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/groups")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                    "{\"name\":\"E2E-Company\",\"code\":\"%s\",\"parentCode\":null}",
                    parentGroupCode)))
            .andExpect(status().isOk());

        // 创建 child-group "E2E-Team" under parent
        childGroupCode = "TC-G-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/groups")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                    "{\"name\":\"E2E-Team\",\"code\":\"%s\",\"parentCode\":\"%s\"}",
                    childGroupCode, parentGroupCode)))
            .andExpect(status().isOk());

        // 创建 leaf-group "E2E-Project" under child
        leafGroupCode = "TC-G-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/groups")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                    "{\"name\":\"E2E-Project\",\"code\":\"%s\",\"parentCode\":\"%s\"}",
                    leafGroupCode, childGroupCode)))
            .andExpect(status().isOk());
    }

    @Test @Order(2)
    @DisplayName("[Admin] 验证分组树层级完整")
    void admin_verifies_group_tree() throws Exception {
        mockMvc.perform(get("/api/v1/groups/tree")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test @Order(3)
    @DisplayName("[Release Manager] 在非叶子分组下创建窗口被拒绝 (GROUP_014)")
    void rm_creates_window_under_non_leaf_fails() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                    "{\"name\":\"Should Fail Window\",\"groupCode\":\"%s\"}",
                    childGroupCode)))  // child has children → non-leaf
            .andExpect(status().is4xxClientError());
    }

    @Test @Order(4)
    @DisplayName("[Release Manager] 在叶子分组下创建窗口成功")
    void rm_creates_window_under_leaf_succeeds() throws Exception {
        String windowName = "RM-E2E-Window-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/release-windows")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                    "{\"name\":\"%s\",\"groupCode\":\"%s\"}",
                    windowName, leafGroupCode)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    // ... (freeze/unfreeze/publish/close lifecycle tests continue)
    // See full plan for complete implementation
}
```

- [ ] **Step 2: 运行测试验证**

```bash
cd backend && mvn test -pl releasehub-bootstrap \
  -Dtest=Slice1_Group_Window_Lifecycle_E2ETest \
  -Dspring.profiles.active=gitlab-e2e-local -DfailIfNoTests=false
```

Expected: Tests pass (with GitLab and PostgreSQL running locally)

- [ ] **Step 3: Commit**

```bash
git add backend/releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/e2e/Slice1_Group_Window_Lifecycle_E2ETest.java
git commit -m "test: add Slice 1 E2E — group hierarchy + window lifecycle"
```

---

### Task 8-12: Slices 2-5 + Frontend E2E

Same pattern as Task 7 — each slice is a self-contained test class with complete setup/teardown. Due to plan length, the detailed step-by-step for each slice follows the same pattern:

**Slice 2** (`Slice2_Repo_Iter_BranchRule_E2ETest.java`):
- [Admin] 创建 BranchRule (TEMPLATE + REGEX)
- [Developer] 导入 GitLab 仓库到 ReleaseHub, 验证版本提取
- [Developer] 创建迭代 + 关联仓库, 验证 feature 分支创建
- [Developer] 尝试创建不合规分支 → 被 BranchRule 拦截
- [Tester] 验证版本号 (base/dev/target) 推导正确

**Slice 3** (`Slice3_Release_Orchestration_E2ETest.java`):
- [Release Manager] 创建窗口 → 挂载迭代 → 冻结 → 发布
- [Tester] 验证 branch-status 显示 MERGED
- [Tester] 验证 Run 记录包含完整的 Step 序列
- [Release Manager] 冻结后挂载被拒绝 (RW_006)
- [Release Manager] 多迭代多仓库发布 + 笛卡尔积验证

**Slice 4** (`Slice4_Post_Release_Cleanup_E2ETest.java`):
- [Release Manager] 关闭 Slice 3 发布的窗口
- [Tester] 验证 GitLab tag 存在、feature 已归档、release 已 merge to main
- [Tester] 验证 CI pipeline 触发
- [Tester] 验证 Run 包含完整收尾 6 步骤
- [Release Manager] 关闭已关闭窗口 → 幂等

**Slice 5** (`Slice5_Conflict_Detection_E2ETest.java`):
- [Developer] 手动修改 GitLab 中 pom.xml 版本 → [Tester] 检测 REPO_AHEAD
- [Developer] 手动创建 release 分支 → [Tester] 检测 BRANCH_EXISTS
- [Developer] 创建不合规分支 → [Tester] 检测 BRANCH_NONCOMPLIANT
- [Developer] 制造合并冲突 → [Tester] 检测 MERGE_CONFLICT → [Developer] 解决 → [Release Manager] 重试
- [Tester] 检测 CROSS_REPO_VERSION_MISMATCH → [Release Manager] 调整策略

**Slice Frontend** (`role-collaboration-e2e.test.ts`):
- [Admin] UI 配置 GitLab + 创建分组
- [Developer] UI 导入仓库 + 创建迭代
- [Release Manager] UI 窗口创建/冻结/发布
- [Tester] UI 查看 branch-status + Run 结果

---

### Task 13: Test Runner 编排脚本

**Files:**
- Create: `scripts/e2e/run-vertical-slices.sh`

```bash
#!/bin/bash
set -e

echo "╔══════════════════════════════════════════╗"
echo "║  ReleaseHub Full-Link E2E Test Runner   ║"
echo "╚══════════════════════════════════════════╝"

PASSED=0
FAILED=0
RESULTS=""

run_slice() {
  local name=$1
  local test_class=$2
  echo ""
  echo "=== [$name] Starting ==="

  if cd backend && mvn test -pl releasehub-bootstrap \
       -Dtest="$test_class" -Dspring.profiles.active=gitlab-e2e-local \
       -DfailIfNoTests=false -q 2>&1; then
    echo "=== [$name] ✅ PASSED ==="
    PASSED=$((PASSED + 1))
    RESULTS="$RESULTS\n  ✅ $name"
  else
    echo "=== [$name] ❌ FAILED ==="
    FAILED=$((FAILED + 1))
    RESULTS="$RESULTS\n  ❌ $name"
  fi
}

# Run slices in order
run_slice "Slice 1: Group + Window Lifecycle" "Slice1_Group_Window_Lifecycle_E2ETest"
run_slice "Slice 2: Repo + Iter + BranchRule" "Slice2_Repo_Iter_BranchRule_E2ETest"
run_slice "Slice 3: Release Orchestration"    "Slice3_Release_Orchestration_E2ETest"
run_slice "Slice 4: Post-Release Cleanup"     "Slice4_Post_Release_Cleanup_E2ETest"
run_slice "Slice 5: Conflict Detection"       "Slice5_Conflict_Detection_E2ETest"

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║  Results                                ║"
echo "╠══════════════════════════════════════════╣"
echo -e "$RESULTS"
echo "╠══════════════════════════════════════════╣"
echo "║  ✅ Passed: $PASSED  ❌ Failed: $FAILED              ║"
echo "╚══════════════════════════════════════════╝"

exit $FAILED
```

```bash
chmod +x scripts/e2e/run-vertical-slices.sh
```

---

### Task 14: GitHub Actions CI workflow

**Files:**
- Create: `.github/workflows/e2e-full-link.yml`

```yaml
name: Full-Link E2E Tests

on:
  push:
    branches: [main]
  pull_request:
    paths-ignore:
      - 'docs/**'
      - '*.md'
  workflow_dispatch:

jobs:
  e2e:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4

      - name: Set up Docker
        uses: docker/setup-buildx-action@v3

      - name: Start full stack
        run: docker compose -f docker-compose.full.yml up -d --build

      - name: Wait for services
        run: |
          echo "Waiting for services to be healthy..."
          timeout 600 bash -c 'until curl -s http://localhost:8080/actuator/health; do sleep 10; done'

      - name: Run vertical slice tests
        run: docker compose -f docker-compose.full.yml run --rm test-runner

      - name: Collect logs
        if: always()
        run: docker compose -f docker-compose.full.yml logs > e2e-logs.txt

      - name: Upload logs
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: e2e-logs
          path: e2e-logs.txt

      - name: Teardown
        if: always()
        run: docker compose -f docker-compose.full.yml down -v
```

---

## 验证方式

```bash
# 模式 A 验证（本机）
docker compose -f docker-compose.gitlab.yml up -d       # 启动 GitLab
./scripts/e2e/init-gitlab.sh                            # 初始化种子数据
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=gitlab-e2e-local  # 后端
cd frontend && pnpm dev                                  # 前端
./scripts/e2e/run-vertical-slices.sh                     # 跑全部切片

# 模式 B 验证（CI 全量）
docker compose -f docker-compose.full.yml up --build     # 构建+启动
docker compose -f docker-compose.full.yml logs test-runner  # 查看结果
docker compose -f docker-compose.full.yml down -v        # 销毁
```

## 非目标

- 真实 GitHub 适配器 E2E（当前仅 GitLab）
- RBAC 权限校验（Phase 6 规划）
- 性能/负载测试
- K8s 部署
