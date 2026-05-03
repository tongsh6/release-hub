# 测试体系重构

> 日期：2026-05-03
> 状态：Proposed

## 一、最终用户/系统行为

开发者能够获得分层清晰、反馈快速、可独立运行的测试体系：

1. **`mvn test`** 在 30 秒内完成纯 Java 单测回归（不加载 Spring，不启动 DB）
2. **`mvn verify`** 按需运行完整集成测试 + E2E
3. **`pnpm test`** 在 10 秒内完成前端单测
4. **`pnpm test:e2e`** 用 Playwright 运行前端 E2E
5. **CI 三条流水线**分别在 PR 阶段提供秒级反馈（单测）和合并后全链路验证（E2E）
6. **Profile 清晰**：`test`（H2+Mock）和 `e2e`（真实 PG+GitLab，两种运行模式）

## 二、完整范围

- [x] 后端 Profile 精简（删除 `unitTest`、`gitlab-e2e-local`，`gitlab-e2e` 合并为 `e2e`）
- [x] 后端 `@ActiveProfiles` 统一（4 个文件 `unitTest` → `test`，1 个基类加 `e2e`）
- [x] 后端 `application-e2e.yml` 重写（env var 注入地址，覆盖 Mode A / Mode B）
- [x] 后端 Maven surefire/failsafe 分离（父 POM pluginManagement，子模块继承）
- [x] 后端 `docker-compose.full.yml` profile 引用更新（`gitlab-e2e` → `e2e`）
- [x] 前端 Vitest 单测补齐（composables、stores、api 层）
- [x] 前端 Puppeteer 自建框架 → Playwright 标准框架迁移（9 个 E2E 文件）
- [x] 前端 `package.json` 测试脚本更新
- [x] CI 新建 `backend-ci.yml`（`mvn test`）
- [x] CI `e2e-full-link.yml` profile 适配（`gitlab-e2e` → `e2e` + env var）
- [x] CI `frontend-ci.yml` 测试脚本对齐
- [ ] 文档更新（`deployment.md` profile 表更新）

## 三、非目标

- 代码覆盖率工具（JaCoCo / Vitest coverage）— 后续单独引入
- 突变测试（Pitest）
- 合约测试（Pact）
- 性能/负载测试
- WireMock 替代 MockRestServiceServer — 后续单独评估
- 生产环境部署配置

## 四、架构形态

### 关键模块

| 模块 | 层 | 职责 |
|------|----|------|
| profile 配置 | Bootstrap (src/main/resources) | `test` / `e2e` Profile YAML，环境变量注入 |
| Maven 插件 | 父 POM | surefire（单测）/ failsafe（集成+E2E）配置 |
| Vitest | 前端 | composable/store/api 单测，jsdom 环境 |
| Playwright | 前端 | E2E 测试，多浏览器并行 |
| CI workflows | .github/workflows | backend-ci + frontend-ci + e2e-full-link |

### Profile 设计

```
test ───────────────────── e2e ─────────────────────
H2 内存库                  真实 PG
Mock GitLab              真实 GitLab
秒级启动                   ┌── Mode A（常驻）：localhost，提前 docker compose up
                          └── Mode B（CI）：Docker 服务名，docker compose up 全栈
```

### Maven 分界

```
surefire（mvn test）          failsafe（mvn verify）
─────────────────────        ─────────────────────────
*Test.java                    *ApiTest.java
（不含 *ApiTest.java、        *IT.java
 *IT.java、*E2eTest.java）    *E2eTest.java
```

### 前端分界

```
vitest run                    playwright test
─────────────────────        ─────────────────────────
*.spec.ts                     e2e/tests/*.e2e.ts
（composable/store/api       （页面级业务流）
 组件单元测试）
```

## 五、阶段计划

一次性完整交付：Profile 精简、Maven 分离、Vitest 补齐、Playwright 迁移、CI 流水线、文档更新。

明确不纳入：JaCoCo 覆盖率、WireMock、突变测试、合约测试、性能测试。

## 六、验收矩阵

| # | 验收标准 | 验证方式 | 关联切片 |
|---|---------|---------|---------|
| 1 | `mvn test` 只跑 surefire 单测，< 30s，全通过 | 命令行 + `grep` surefire 输出不含 *ApiTest | Slice 1, 2 |
| 2 | `mvn verify` runs failsafe 集成+E2E | 命令行 + failsafe 输出包含 *ApiTest/*IT/*E2eTest | Slice 2 |
| 3 | `pnpm test` 返回非零测试数（不再是 1 个 HelloWorld） | `pnpm test --reporter=verbose` | Slice 3 |
| 4 | `pnpm test:e2e` 用 Playwright 跑通 login + navigation | Playwright HTML report | Slice 4 |
| 5 | `Application-unitTest.yml` 和 `application-gitlab-e2e-local.yml` 已删除，所有引用已更新 | `grep -r "unitTest\|gitlab-e2e-local"` 无结果 | Slice 1 |
| 6 | CI backend-ci.yml 在 PR 时触发且 `mvn test` 通过 | GitHub Actions 日志 | Slice 5 |
| 7 | CI e2e-full-link.yml profile 为 `e2e` 且全链路通过 | GitHub Actions 日志 | Slice 5 |
| 8 | Mode A 和 Mode B 可在本机并行运行，端口不冲突 | `docker ps` 端口检查 | Slice 1 |

## 七、风险与回滚

| 风险 | 影响 | 缓解措施 | 回滚路径 |
|------|------|---------|---------|
| surefire 排除规则遗漏，`mvn test` 跑 0 个测试 | 本地开发无回归保护 | 上线前对比 `mvn test` before/after 测试数量 | revert pom.xml，git revert |
| failsafe include 规则误匹配，`mvn verify` 跑错测试 | CI 失败或漏测 | 每个模块 verify 后手检 failsafe 报告 | 修正 include pattern |
| Playwright 迁移中 E2E 行为退化 | CI E2E 失败 | 逐文件迁移，每迁移一个跑通后再下一个 | Puppeteer 代码保留到全部迁移完成才删除 |
| `application-e2e.yml` 环境变量默认值与现网不一致 | Mode A 本地跑 E2E 失败 | Mode A 用默认值（localhost），Mode B 用 docker-compose env var 覆盖 | 调整默认值或 env var 名 |
| AbstractGitLabE2ETest 加 `@ActiveProfiles("e2e")` 后其他测试配置冲突 | Slice 测试失败 | 检查 e2e profile 与 AbstractGitLabE2ETest 的 @DynamicPropertySource 是否冲突 | 移除 @ActiveProfiles，额外加一个 AbstractModeATest 基类 |

## 八、切片拆分

### DAG

```
确认设计
  ├── Slice 1: Profile 精简 + @ActiveProfiles 统一（无依赖，纯配置）
  │     └── Slice 2: Maven surefire/failsafe 分离（依赖 Slice 1 — failsafe 配置依赖 profile 清晰）
  ├── Slice 3: 前端 Vitest 补齐（无依赖，独立前端）
  │     └── Slice 4: Playwright 迁移（依赖 Slice 3 — playwright 依赖已安装，写法参考 Vitest 模式）
  ├── Slice 5: CI 流水线重构（依赖 Slice 2 + Slice 4）
  └── Slice 6: 文档更新 + 静态扫描（依赖 Slice 1-5）
```

### 切片概览

| Slice | 名称 | 涉及文件 | 依赖 | 预估 |
|-------|------|---------|------|------|
| 1 | Profile 精简 | 6 YAML + 5 Java 注解 | 无 | 小 |
| 2 | Maven Phase 分离 | 父 POM + bootstrap POM | Slice 1 | 中 |
| 3 | 前端 Vitest 补齐 | composable/store/api 测试文件 | 无 | 中 |
| 4 | Playwright 迁移 | 9 E2E 文件 + 工具函数 + playwright.config.ts | Slice 3 | 大 |
| 5 | CI 流水线重构 | 3 个 GitHub Actions yml + docker-compose 引用 | Slice 2, 4 | 中 |
| 6 | 文档更新 + 静态扫描 | deployment.md + 扫描报告 | Slice 1-5 | 小 |
