# 测试体系重构 推进计划

> 日期：2026-05-03
> 状态：收尾整改中（功能骨架已落地，质量门禁与文档一致性未完成）
> 关联设计：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md`

## 目标

全栈测试体系重构，一次性完整交付：Profile 精简、Maven 构建基础设施（surefire/failsafe/JaCoCo/Pitest）、WireMock、前端测试（Vitest+coverage+Playwright）、Pact 合约测试、CI 流水线。

## 当前复核结论（2026-05-04）

结论：功能骨架已基本落地，但不应按“完全完成”验收；当前状态应按“收尾整改中”管理。Profile、WireMock、Playwright、Pact、CI workflow 均已有实现痕迹，前端单测与 Pact consumer 可运行；但核心质量承诺尚未闭合，尤其是后端快慢测试分层和覆盖率门禁。

实测结果：

| 验证项 | 结果 | 结论 |
|--------|------|------|
| `cd backend && mvn -q -DskipTests validate` | 通过 | Maven 基础配置可解析 |
| `cd backend && mvn -q test` | 未达到设计目标 | surefire 实际执行了 `io.releasehub.bootstrap.e2e.*E2eTest`，并触发真实 GitLab 地址访问；`mvn test` 不是纯单测回归 |
| `cd frontend && pnpm test` | 5 files / 18 tests 通过，约 335ms | 前端基础单测已补齐且反馈快 |
| `cd frontend && pnpm test --coverage` | 通过；Statements 10.73%、Branches 5.21%、Functions 8%、Lines 10.84% | coverage 可生成，但仅靠占位阈值过线 |
| `cd frontend && pnpm test:pact` | 5 files / 6 tests 通过 | Pact consumer 测试可运行 |

主要阻塞：

| 优先级 | 结论 | 影响 |
|--------|------|------|
| P0 | 后端 surefire/failsafe 只匹配 `*E2ETest.java`，但仓库存在 9 个 `*E2eTest.java` | `mvn test` 会误跑 E2E，破坏 30 秒纯单测目标，也可能访问真实外部 GitLab |
| P0 | 后端覆盖率阈值为 0.10-0.40，占位低于设计目标 0.30-0.80 | `mvn verify -Pcoverage` 有门禁形式，但质量强度不达标 |
| P1 | `pnpm test` 不含 coverage；coverage 需 `pnpm test --coverage` | 开发者命令与设计中的“单测 + 覆盖率”语义不一致 |
| P1 | `puppeteer` 依赖、lockfile 与 `frontend/e2e/README.md` 仍残留 | Playwright 迁移未完成收尾，文档会误导后续维护 |
| P1 | 任务记录外仍有旧 profile / Puppeteer 文档引用 | “文档更新 + 静态扫描完成”的记录不可信 |

完成度判断：功能骨架约 70%，质量门禁约 45%，文档一致性约 35%。下一步应先修 P0，再更新设计文档状态与验收记录。

## DAG

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

## 切片概览

| Slice | 名称 | 涉及层 | 依赖 | 状态 |
|-------|------|--------|------|------|
| 1 | Profile 精简 + @ActiveProfiles 统一 | Bootstrap config | 无 | ✅ 已完成 |
| 2 | Maven 构建基础设施 | Build (POM) | 无 | ⚠️ 需整改：`*E2eTest.java` 未被 failsafe/surefire 规则覆盖，覆盖率阈值占位 |
| 3 | WireMock 实现 | Infrastructure test | Slice 2 | ✅ 已完成 |
| 4 | 前端 Vitest + Coverage | Frontend test | 无 | ⚠️ 需整改：`pnpm test` 不含 coverage，阈值临时占位 |
| 5 | Playwright 迁移 | Frontend e2e | Slice 4 | ⚠️ 需整改：`puppeteer` 依赖与旧文档残留 |
| 6 | Pact 合约测试 | Backend + Frontend | Slice 2, 5 | ✅ 已完成 |
| 7 | CI 流水线 | CI (GitHub Actions) | Slice 2-6 | ✅ 已完成 |
| 8 | 文档 + 静态扫描 | Docs | Slice 1-7 | ⚠️ 需整改：仍有 stale profile / Puppeteer 文档引用 |

## 验收矩阵

| # | 验收标准 | 验证方式 | 关联切片 | 复核状态 |
|---|---------|---------|---------|----------|
| 1 | `mvn test` 只跑 surefire，< 30s | 命令行 + 报告 | Slice 1, 2 | ❌ 未满足：`*E2eTest.java` 会被 surefire 匹配 |
| 2 | `mvn verify` runs failsafe 集成+E2E | failsafe 报告 | Slice 2 | ⚠️ 部分满足：`*E2ETest.java` 匹配，`*E2eTest.java` 漏匹配 |
| 3 | `mvn verify -Pcoverage` 报告生成 + 不达标阻断 | jacoco check | Slice 2 | ⚠️ 部分满足：有 check，但阈值是 0.10-0.40 占位 |
| 4 | `mvn verify -Ppitest` 报告生成 | pitest report | Slice 2 | ✅ 配置存在，需保留执行日志佐证 |
| 5 | WireMock 替代 MockRestServiceServer | 检查 infrastructure 测试代码 | Slice 3 | ✅ 已满足 |
| 6 | `pnpm test` 测试数 > 10 + 覆盖率 > 阈值 | vitest --coverage | Slice 4 | ⚠️ 部分满足：`pnpm test` 不带 coverage，阈值占位 |
| 7 | `pnpm test:e2e` Playwright 全通过 | Playwright HTML report | Slice 5 | ⚠️ Playwright 已配置，仍需清理 Puppeteer 残留并保留通过日志 |
| 8 | `pnpm test:pact` 验证通过 | Pact 报告 | Slice 6 | ✅ 配置存在，需保留执行日志佐证 |
| 9 | 测试 profile 仅 test/e2e 两个 YAML | `ls resources/` | Slice 1 | ✅ 配置文件层面已满足，文档仍有 stale 引用 |
| 10 | CI 三条流水线均通过 | GitHub Actions | Slice 7 | ⚠️ workflow 存在，质量强度受上述门禁问题影响 |

## 待整改清单

| 优先级 | 问题 | 处理动作 | 归属 |
|--------|------|----------|------|
| P0 | 后端 `*E2eTest.java` 未被 failsafe/surefire 规则覆盖 | 统一测试类命名为 `*E2ETest.java`，或在 surefire/failsafe 同时补齐 `**/*E2eTest.java` include/exclude | Slice 2 |
| P0 | 覆盖率门禁仍是占位阈值 | 要么上调到设计目标，要么更新设计为阶段性阈值并建立提升任务 | Slice 2, 4 |
| P1 | `pnpm test` 与“单测 + 覆盖率”承诺不一致 | 新增/规范 `test:coverage`，或让 `test` 默认带 coverage，并同步 CI/README | Slice 4, 7, 8 |
| P1 | Puppeteer 迁移残留 | 移除 `puppeteer` 依赖和 lockfile 残留，更新 `frontend/e2e/README.md` 及相关 docs | Slice 5, 8 |
| P1 | 文档 stale 引用 | 清理 `application-unitTest.yml`、Puppeteer 等过期描述；设计页切片状态同步为真实状态 | Slice 8 |

## 不纳入

性能/负载测试（JMeter/k6）、生产环境部署。

## 执行日志

| Slice | 开始 | 结束 | 结果 |
|-------|------|------|------|
| 1 | 2026-05-03 | 2026-05-03 | ✅ 完成 |
| 2 | 2026-05-03 | 2026-05-03 | ⚠️ 需整改：E2E 命名匹配遗漏 + 阈值占位 |
| 3 | 2026-05-03 | 2026-05-03 | ✅ 完成 |
| 4 | 2026-05-03 | 2026-05-03 | ⚠️ 需整改：coverage 脚本语义 + 阈值占位 |
| 5 | 2026-05-03 | 2026-05-03 | ⚠️ 需整改：Puppeteer 残留 |
| 6 | 2026-05-03 | 2026-05-04 | ✅ 完成 |
| 7 | 2026-05-03 | 2026-05-04 | ✅ 完成 |
| 8 | 2026-05-03 | 2026-05-04 | ⚠️ 需整改：文档一致性不足 |
