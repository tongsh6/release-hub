# 测试体系重构

> 日期：2026-05-03 | 状态：Proposed

## 一、最终用户/系统行为

开发者能够获得分层清晰、反馈快速、可独立运行、有量化质量度量的测试体系：

1. **`mvn test`** 在 30 秒内完成纯 Java 单测回归
2. **`mvn verify`** 按需运行完整集成测试 + E2E
3. **`mvn verify -Pcoverage`** 生成 JaCoCo 覆盖率报告，不达标阻断构建
4. **`mvn verify -Ppitest`** 按需运行突变测试
5. **`pnpm test`** 在 10 秒内完成前端单测 + 覆盖率
6. **`pnpm test:e2e`** 用 Playwright 运行前端 E2E
7. **`pnpm test:pact`** 验证前后端 API 合约
8. **CI 三条流水线**：PR 秒级反馈、合并后全链路、覆盖率门禁
9. **Profile 清晰**：`test`（H2 + Mock GitLab）和 `e2e`（真实 PG+GitLab，两种模式）

## 二、完整范围

- [x] 后端 Profile 精简（删 `unitTest`、`gitlab-e2e-local`，`gitlab-e2e` 合并为 `e2e`）
- [x] 后端 `@ActiveProfiles` 统一（4 文件 → `test`，1 基类加 `e2e`）
- [x] 后端 `application-e2e.yml` 重写（env var 注入，Mode A/B 覆盖）
- [x] 后端 Maven surefire/failsafe 分离（父 POM pluginManagement）
- [x] 后端 JaCoCo 覆盖率（Maven profile `coverage`，按模块设阈值，CI 门禁）
- [x] 后端 Pitest 突变测试（Maven profile `pitest`，按需运行）
- [x] 后端 WireMock 替代 MockRestServiceServer（infrastructure 适配器测试）
- [x] 前端 Vitest 单测补齐（composables/stores/api 层）
- [x] 前端 Vitest coverage（c8 或 istanbul provider，最低阈值）
- [x] 前端 Puppeteer → Playwright 迁移（9 个 E2E 文件 + 工具函数）
- [x] 前后端 Pact 合约测试（consumer 前端 + provider 后端）
- [x] CI `backend-ci.yml` 新建（`mvn test` + JaCoCo）
- [x] CI `e2e-full-link.yml` profile 适配（`gitlab-e2e` → `e2e`）
- [x] CI `frontend-ci.yml` 测试/覆盖率脚本更新
- [x] 文档更新（`deployment.md` profile 表 + README 测试命令）
- [x] 静态扫描留痕

## 三、非目标

- 性能/负载测试（JMeter/k6）
- 生产环境部署配置
- RBAC 权限测试（Phase 6 随 RBAC 实现一起做）

## 四、架构形态

### 关键模块

| 模块 | 层 | 职责 |
|------|----|------|
| Spring Profile YAML | Bootstrap config | `test` / `e2e` 配置，env var 注入 |
| Maven 插件 | 父 POM + 子模块 POM | surefire/failsafe/JaCoCo（按模块阈值 80/70/50/30%）/Pitest |
| WireMock | Infrastructure test | 独立端口 stub GitLab API |
| Vitest + coverage | 前端 | composable/store/api 单测 |
| Playwright | 前端 | E2E 页面级测试 |
| Pact | 前后端 | API 合约验证 |
| GitHub Actions | CI | backend-ci / frontend-ci / e2e-full-link |

### Profile 设计

```
test ───────────────────── e2e ─────────────────────
H2 内存库                  真实 PG
Mock GitLab               真实 GitLab
秒级启动                   两种模式
                          ┌── Mode A（常驻）：localhost
                          └── Mode B（CI）：Docker 服务名
```

### Maven Phase + Profile

```
mvn test                              mvn verify -Pcoverage
─────────────────────                 ─────────────────────────
surefire: *Test.java                  surefire: *Test.java
（单测，不加载 Spring）               failsafe: *ApiTest + *IT + *E2eTest
                                      jacoco: report + check
                                      
mvn verify -Ppitest
─────────────────────────
pitest: mutation coverage
```

### 前端分界

```
vitest run                            playwright test
─────────────────────                 ─────────────────────────
*.spec.ts                             e2e/tests/*.ts
（composable/store/api）              （页面级业务流）
+ coverage                            + HTML report

pact test
─────────────────────────
consumer + provider
```

## 五、阶段计划

一次性完整交付，无 Phase 2。

## 六、验收矩阵

| # | 验收标准 | 验证方式 | 关联切片 |
|---|---------|---------|---------|
| 1 | `mvn test` 只跑 surefire，< 30s | 命令行 + 报告 | Slice 1, 2 |
| 2 | `mvn verify` runs failsafe 集成+E2E | failsafe 报告 | Slice 2 |
| 3 | `mvn verify -Pcoverage` JaCoCo 报告生成，阈值不达标阻断 | jacoco report + `check` goal | Slice 2 |
| 4 | `mvn verify -Ppitest` 突变测试报告生成 | pitest report | Slice 2 |
| 5 | infrastructure 测试使用 WireMock，不再用 MockRestServiceServer | 检查测试代码 | Slice 3 |
| 6 | `pnpm test` 测试数 > 10 + 覆盖率 > 阈值 | vitest --reporter=verbose --coverage | Slice 4 |
| 7 | `pnpm test:e2e` Playwright 全通过 | Playwright HTML report | Slice 5 |
| 8 | `pnpm test:pact` 前后端合约一致 | Pact 报告 | Slice 6 |
| 9 | 测试 profile 从 4 个减到 2 个（test/e2e），`unitTest`/`gitlab-e2e`/`gitlab-e2e-local` 已删除 | `ls resources/application-*.yml` 不含这些文件 | Slice 1 |
| 10 | CI backend-ci.yml PR 触发且 `mvn verify -Pcoverage` 通过 | Actions 日志 | Slice 7 |
| 11 | CI e2e-full-link.yml profile 为 `e2e` | Actions 日志 | Slice 7 |

## 七、风险与回滚

| 风险 | 影响 | 缓解措施 | 回滚路径 |
|------|------|---------|---------|
| surefire 排除规则遗漏 | 单测跑 0 个 | 上线前对比测试数量 | git revert POM |
| failsafe include 误匹配 | CI 失败 | 逐模块 verify 手检报告 | 修正 pattern |
| Playwright 迁移行为退化 | E2E 失败 | 逐文件迁移，逐个验证 | Puppeteer 代码保留到最后 |
| WireMock 与 GitLab API 行为偏差 | stub 过时 | 录制真实 GitLab 响应 | 恢复 MockRestServiceServer |
| Pitest 耗时长 | CI 超时 | 单独 `-Ppitest` profile，非默认 | 关掉 profile |
| Pact 合约验证复杂度 | 前后端需要协调 | 先做核心 API，逐步扩展 | 移除 Pact 配置 |

## 八、切片拆分

### DAG

```
确认设计
  ├── Slice 1: Profile 精简 + @ActiveProfiles 统一（无依赖）
  ├── Slice 2: Maven 构建基础设施（无依赖，与 1 并行）
  │     └── Slice 3: WireMock 实现（依赖 Slice 2）
  ├── Slice 4: 前端 Vitest + Coverage（无依赖，与 1-2 并行）
  │     └── Slice 5: Playwright 迁移（依赖 Slice 4）
  ├── Slice 6: Pact 合约测试（依赖 Slice 2 + Slice 5）
  ├── Slice 7: CI 流水线（依赖 Slice 2-6）
  └── Slice 8: 文档 + 静态扫描（依赖 Slice 1-7）
```

### 切片概览

| Slice | 名称 | 涉及文件 | 依赖 | 状态 |
|-------|------|---------|------|------|
| 1 | Profile 精简 | 6 YAML + 5 Java + 1 docker-compose | 无 | ⬜ |
| 2 | Maven 构建基础设施 | 父 POM + bootstrap POM + infra POM | 无 | ⬜ |
| 3 | WireMock 实现 | infrastructure 测试文件（~6 个） | Slice 2 | ⬜ |
| 4 | 前端 Vitest + Coverage | 10+ 前端文件 | 无 | ⬜ |
| 5 | Playwright 迁移 | 9 E2E 文件 + config + 旧工具删除 | Slice 4 | ⬜ |
| 6 | Pact 合约测试 | 12 文件（5 consumer + 5 provider + 2 config） | Slice 2, 5 | ⬜ |
| 7 | CI 流水线 | 3 workflow + docker-compose | Slice 2-6 | ⬜ |
| 8 | 文档 + 静态扫描 | deployment.md + README + 报告 | Slice 1-7 | ⬜ |
