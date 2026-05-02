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

5 条垂直场景链路，覆盖全部 11 个用户故事。Group（分组约束）、Branch Rule（分支规则校验）、System Settings（系统配置）作为横切关注点，贯穿各条切片的业务流中验证。

---

### Slice 1: 分组层级约束 + 发布窗口完整生命周期（US-GROUP + US-RW + US-SET）

系统设置和分组体系是所有功能的前置依赖，发布窗口的创建受分组层级约束。

```
Step 1: 系统设置 — 配置 GitLab 连接
  保存 GitLab baseUrl + token → 验证 TEST CONNECTION 成功
  保存分支命名模板（feature/release）、基线 refs

Step 2: 构建分组层级树
  创建 parent-group（company）
    → 创建 child-group（team）under parent
      → 创建 leaf-group（project）under child
  验证：分组 code 自动生成且唯一
  验证：GET /groups/tree 返回完整层级

Step 3: 分组约束验证（横切）
  尝试在 parent-group 下创建发布窗口 → 拒绝（GROUP_014：非叶子节点）
  尝试在 child-group 下创建发布窗口 → 拒绝
  在 leaf-group 下创建发布窗口 → 成功

Step 4: 发布窗口完整生命周期
  创建窗口(DRAFT) → 配置时间窗口 → 验证状态
  → 冻结 → 验证 frozen=true，修改窗口被拒绝
  → 解冻 → 验证 frozen=false
  → 发布(PUBLISHED) → 验证 publishedAt 已设置
  → 关闭(CLOSED) → 验证状态不可逆，再次关闭幂等
```

**横切验证点**：Group 层级约束在窗口、仓库、迭代创建时均生效（Step 3 仅展示窗口场景，仓库和迭代的 Group 约束在后续 Slice 中类似验证）。

---

### Slice 2: 仓库创建 + 版本号提取 + 迭代关联 + 分支规则校验（US-REPO + US-IT + US-BR）

分支规则在 feature 分支创建时生效，不合规的分支命名应被拦截。

```
Step 1: 配置分支规则
  创建 BranchRule（TEMPLATE: "feature/{iterationKey}"，scope=GLOBAL，enabled=true）
  创建 BranchRule（REGEX: "^feature/[A-Z]+-\\d+$"，scope=PROJECT，enabled=true）
  验证：test API 分别返回 match/mismatch

Step 2: 创建仓库（种子仓库通过 GitLab API 导入到 ReleaseHub）
  导入 seed-repo-1（Maven 单模块，version=1.4.0）
    → VersionExtractor 从 pom.xml 提取版本号：1.4.0
  导入 seed-repo-2（Gradle，version=3.0.0）
    → VersionExtractor 从 gradle.properties 提取版本号：3.0.0
  验证：仓库创建必须在 leaf-group 下

Step 3: 创建迭代 + 关联仓库 → 分支规则校验
  创建迭代 ITER-1 → 关联 seed-repo-1
    → GitLab API 创建 feature 分支：feature/ITER-1（符合 TEMPLATE 规则 ✓）
    → 验证：GitLab 中实际存在 feature/ITER-1 分支
    → 验证：dev_version = 1.5.0-SNAPSHOT（MINOR+1）
    → 验证：base_version = 1.4.0，target_version = 1.5.0

  创建迭代 ITER-2 → 尝试创建分支名 "hotfix/urgent"（不符合任何规则）
    → 验证：分支创建被拦截，返回合规错误
    → 验证：GitLab 中不存在该分支

Step 4: 版本号推导演练
  seed-repo-1 (Maven)：1.4.0 → MINOR bump → 1.5.0-SNAPSHOT
  seed-repo-2 (Gradle)：3.0.0 → MAJOR bump → 4.0.0-SNAPSHOT
  验证：不同 BumpRule 在同一个迭代中正确应用于各自仓库
```

**横切验证点**：Branch Rule 在 Step 3 中首次介入——feature 分支被创建时即受到规则校验。后续 Slice 3 中 release 分支创建同样受规则约束。

---

### Slice 3: 发布执行编排（US-REL）

真实 GitLab 上的全自动编排：分支创建 → 合并 → 冲突检测 → Run 追踪。

```
Step 1: 准备工作（复用 Slice 2 的数据）
  发布窗口 RW-1（在 leaf-group 下）
  迭代 ITER-1（关联 seed-repo-1，feature/ITER-1 已存在）

Step 2: 挂载迭代到窗口
  POST /release-windows/{id}/attach → 验证 release/RW-1 在 GitLab 中创建
  → 验证：release 分支命名是否符合 BranchRule（如配置了 release 命名模板）
  → 验证：feature/ITER-1 已 merge 到 release/RW-1
  → 验证：GitLab 中存在对应的 merge request（已合并状态）

Step 3: 冻结 → 发布
  Freeze → 验证 frozen=true
  Publish
    → 验证：Run 记录已创建
    → 验证：RunItem 包含 repo-1
    → 验证：RunStep 序列 = ENSURE_FEATURE → ENSURE_RELEASE → ENSURE_MR → TRY_MERGE
    → 验证：每个 Step 状态为 COMPLETED
    → 验证：branch-status API 返回 mergeStatus=MERGED

Step 4: 多仓库发布（笛卡尔积）
  创建另一个迭代 ITER-2 关联 seed-repo-2
  → 挂载到 RW-1
  → Publish
  → 验证：Run 中有 2 个 RunItem（ITER-1×repo-1 + ITER-2×repo-2）
  → 验证：每个 RunItem 独立执行完整步骤链

Step 5: 冻结窗口拒绝挂载（横切约束）
  Freeze RW-2 → 尝试 attach → 返回 RW_006
```

**横切验证点**：Step 2 中 release 分支的命名同样受 BranchRule 约束。Step 5 中冻结状态是窗口生命周期对挂载操作的横切约束。

---

### Slice 4: 发布收尾清理（US-POST）

真实 GitLab 上的全自动收尾：归档 → merge to master → 打 tag → 触发 CI。

```
Step 1: 关闭 Slice 3 的 RW-1
  POST /release-windows/{id}/close
    → 验证：窗口状态变为 CLOSED
    → 验证：Run 记录包含收尾步骤

Step 2: 逐项验证 GitLab 实际变更
  验证：迭代 ITER-1 状态为 CLOSED
  验证：target_version = 1.5.0（移除 -SNAPSHOT）
  验证：feature/ITER-1 已重命名为 archive/released/feature/ITER-1
  验证：release/RW-1 已 merge 到 main
  验证：tag v1.5.0 在 GitLab tags 列表中存在
  验证：CI pipeline 被触发（检查 GitLab pipeline 记录）

Step 3: 验证 Run 步骤完整性
  RunStep 序列 = CLOSE_ITERATION → UPDATE_VERSION → ARCHIVE_BRANCH
                → MERGE_TO_MASTER → CREATE_TAG → TRIGGER_CI
  验证：全部 6 个 Step 状态为 COMPLETED

Step 4: 关闭幂等性
  对已关闭窗口再次 close → 200 OK，无副作用
```

---

### Slice 5: 冲突检测 + 阻断 + 恢复重试（US-VAL）

7 种冲突类型的真实触发与解决。Branch Rule 的生产环境验证贯穿其中。

```
Step 1: 版本不匹配冲突（MISMATCH / REPO_AHEAD / SYSTEM_AHEAD）
  创建窗口 → 挂载迭代（关联 seed-repo-1）
  → 直接在 GitLab 中修改 feature 分支的 pom.xml：1.5.0-SNAPSHOT → 1.6.0-SNAPSHOT
  → 在 ReleaseHub 中触发 "同步版本号"
  → 验证：ConflictItem 出现 REPO_AHEAD（仓库版本超前）
  → 选择解决方式："使用系统版本"
  → 验证：GitLab 中 pom.xml 恢复为 1.5.0-SNAPSHOT

Step 2: 分支已存在冲突（BRANCH_EXISTS）
  挂载迭代 ITER-2 到窗口 RW-3
  → 手动在 GitLab 中创建 release/RW-3（模拟冲突）
  → Publish
  → 验证：ConflictItem 包含 BRANCH_EXISTS
  → 删除冲突分支 → 重新 Publish → 成功

Step 3: 分支名不合规冲突（BRANCH_NONCOMPLIANT）
  创建 BranchRule（TEMPLATE: "feature/*"）
  → 某仓库存在不符合规则的分支 "temp/experiment"
  → 扫描冲突
  → 验证：ConflictItem 包含 BRANCH_NONCOMPLIANT
  → 归档或删除不合规分支 → 重新扫描 → 清理

Step 4: Git 合并冲突（MERGE_CONFLICT）
  在 seed-repo-1 中：
    main 分支修改 pom.xml 的同一行 → commit
    feature/ITER-1 分支也修改 pom.xml 同一行 → commit
  → 挂载迭代 → 发布
  → 验证：checkMergeability 返回 conflict
  → 验证：Run 状态为 FAILED
  → 在 GitLab 中手动解决冲突（合并 MR）
  → 调用 POST /runs/{id}/retry
  → 验证：重试后 Run 状态变为 SUCCESS

Step 5: 跨仓库版本不一致（CROSS_REPO_VERSION_MISMATCH）
  同一窗口下：
    seed-repo-1 target=1.5.0（MINOR bump）
    seed-repo-2 target=4.0.0（MAJOR bump）
  → 扫描冲突
  → 验证：ConflictItem 包含 CROSS_REPO_VERSION_MISMATCH
  → 调整 version policy 使二者对齐 → 重新扫描 → 清理
```

**横切验证点**：Step 3 将 Branch Rule 与冲突检测系统打通——不合规分支在发布前被冲突扫描识别并阻断。这是分支规则从"配置生效"到"业务阻断"的完整链路。

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

echo "=== Slice 1: 分组层级 + 发布窗口生命周期 ==="
cd backend && mvn test -Dtest=Slice1_Group_RW_Lifecycle -Dprofile=gitlab-e2e

echo "=== Slice 2: 仓库创建 + 版本号 + 迭代 + 分支规则 ==="
cd backend && mvn test -Dtest=Slice2_Repo_Iter_BranchRule -Dprofile=gitlab-e2e

echo "=== Slice 3: 发布执行编排 ==="
cd backend && mvn test -Dtest=Slice3_Release_Orchestration -Dprofile=gitlab-e2e

echo "=== Slice 4: 发布收尾清理 ==="
cd backend && mvn test -Dtest=Slice4_Post_Release_Cleanup -Dprofile=gitlab-e2e

echo "=== Slice 5: 冲突检测 + 阻断 + 恢复 ==="
cd backend && mvn test -Dtest=Slice5_Conflict_Detection -Dprofile=gitlab-e2e

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
