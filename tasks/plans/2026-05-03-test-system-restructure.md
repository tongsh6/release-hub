# 测试体系重构 推进计划

> 日期：2026-05-03
> 状态：待启动
> 关联设计：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md`

## 目标

梳理并重构 ReleaseHub 全栈测试体系，一次性完整交付：Profile 精简、Maven Phase 分离、Vitest 补齐、Playwright 迁移、CI 流水线重构。

## DAG

```
确认设计
  ├── Slice 1: Profile 精简 + @ActiveProfiles 统一（无依赖，纯配置）
  │     └── Slice 2: Maven surefire/failsafe 分离（依赖 Slice 1）
  ├── Slice 3: 前端 Vitest 补齐（无依赖，与 Slice 1-2 并行）
  │     └── Slice 4: Playwright 迁移（依赖 Slice 3）
  ├── Slice 5: CI 流水线重构（依赖 Slice 2 + Slice 4）
  └── Slice 6: 文档更新 + 静态扫描（依赖 Slice 1-5）
```

## 切片概览

| Slice | 名称 | 涉及层 | 依赖 | 状态 |
|-------|------|--------|------|------|
| 1 | Profile 精简 + @ActiveProfiles 统一 | Bootstrap (config) | 无 | ⬜ 待启动 |
| 2 | Maven surefire/failsafe 分离 | Build (POM) | Slice 1 | ⬜ 待启动 |
| 3 | 前端 Vitest 补齐 | Frontend (test) | 无 | ⬜ 待启动 |
| 4 | Playwright 迁移 | Frontend (e2e) | Slice 3 | ⬜ 待启动 |
| 5 | CI 流水线重构 | CI (GitHub Actions) | Slice 2, 4 | ⬜ 待启动 |
| 6 | 文档更新 + 静态扫描 | Docs | Slice 1-5 | ⬜ 待启动 |

## 验收矩阵

| # | 验收标准 | 验证方式 | 关联切片 |
|---|---------|---------|---------|
| 1 | `mvn test` 只跑 surefire 单测，< 30s，全通过 | 命令行 + surefire 报告 | Slice 1, 2 |
| 2 | `mvn verify` runs failsafe 集成+E2E | 命令行 + failsafe 报告 | Slice 2 |
| 3 | `pnpm test` 返回非零测试数 | `pnpm test --reporter=verbose` | Slice 3 |
| 4 | `pnpm test:e2e` 用 Playwright 跑通核心业务流 | Playwright HTML report | Slice 4 |
| 5 | `application-unitTest.yml` + `application-gitlab-e2e-local.yml` 已删除 | `grep -r` 无引用 | Slice 1 |
| 6 | CI `backend-ci.yml` PR 触发且 `mvn test` 通过 | GitHub Actions 日志 | Slice 5 |
| 7 | CI `e2e-full-link.yml` profile 为 `e2e` 且全链路通过 | GitHub Actions 日志 | Slice 5 |
| 8 | Mode A / Mode B 本机并行，端口不冲突 | `docker ps` 端口检查 | Slice 1 |

## 不纳入

代码覆盖率（JaCoCo/Vitest coverage）、WireMock、突变测试、合约测试、性能测试。

## 执行日志

| Slice | 开始 | 结束 | 结果 |
|-------|------|------|------|
| 1 | — | — | — |
| 2 | — | — | — |
| 3 | — | — | — |
| 4 | — | — | — |
| 5 | — | — | — |
| 6 | — | — | — |
