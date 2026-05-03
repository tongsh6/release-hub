# 测试体系重构 推进计划

> 日期：2026-05-03
> 状态：已完成
> 关联设计：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md`

## 目标

全栈测试体系重构，一次性完整交付：Profile 精简、Maven 构建基础设施（surefire/failsafe/JaCoCo/Pitest）、WireMock、前端测试（Vitest+coverage+Playwright）、Pact 合约测试、CI 流水线。

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
| 2 | Maven 构建基础设施 | Build (POM) | 无 | ✅ 已完成 |
| 3 | WireMock 实现 | Infrastructure test | Slice 2 | ✅ 已完成 |
| 4 | 前端 Vitest + Coverage | Frontend test | 无 | ✅ 已完成（阈值临时占位） |
| 5 | Playwright 迁移 | Frontend e2e | Slice 4 | ✅ 已完成 |
| 6 | Pact 合约测试 | Backend + Frontend | Slice 2, 5 | ✅ 已完成 |
| 7 | CI 流水线 | CI (GitHub Actions) | Slice 2-6 | ✅ 已完成 |
| 8 | 文档 + 静态扫描 | Docs | Slice 1-7 | ✅ 已完成 |

## 验收矩阵

| # | 验收标准 | 验证方式 | 关联切片 |
|---|---------|---------|---------|
| 1 | `mvn test` 只跑 surefire，< 30s | 命令行 + 报告 | Slice 1, 2 |
| 2 | `mvn verify` runs failsafe 集成+E2E | failsafe 报告 | Slice 2 |
| 3 | `mvn verify -Pcoverage` 报告生成 + 不达标阻断 | jacoco check | Slice 2 |
| 4 | `mvn verify -Ppitest` 报告生成 | pitest report | Slice 2 |
| 5 | WireMock 替代 MockRestServiceServer | 检查 infrastructure 测试代码 | Slice 3 |
| 6 | `pnpm test` 测试数 > 10 + 覆盖率 > 阈值 | vitest --coverage | Slice 4 |
| 7 | `pnpm test:e2e` Playwright 全通过 | Playwright HTML report | Slice 5 |
| 8 | `pnpm test:pact` 验证通过 | Pact 报告 | Slice 6 |
| 9 | 测试 profile 仅 test/e2e 两个 YAML | `ls resources/` | Slice 1 |
| 10 | CI 三条流水线均通过 | GitHub Actions | Slice 7 |

## 不纳入

性能/负载测试（JMeter/k6）、生产环境部署。

## 执行日志

| Slice | 开始 | 结束 | 结果 |
|-------|------|------|------|
| 1 | 2026-05-03 | 2026-05-03 | ✅ 完成 |
| 2 | 2026-05-03 | 2026-05-03 | ✅ 完成（阈值占位） |
| 3 | 2026-05-03 | 2026-05-03 | ✅ 完成 |
| 4 | 2026-05-03 | 2026-05-03 | ✅ 完成（阈值占位） |
| 5 | 2026-05-03 | 2026-05-03 | ✅ 完成 |
| 6 | 2026-05-03 | 2026-05-04 | ✅ 完成 |
| 7 | 2026-05-03 | 2026-05-04 | ✅ 完成 |
| 8 | 2026-05-03 | 2026-05-04 | ✅ 完成 |
