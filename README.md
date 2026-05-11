# ReleaseHub

**多仓库发布编排与自动化平台** — 统一管理多个 Git 仓库的发布窗口、版本策略和分支规则。

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-green.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5+-blue.svg)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9+-blue.svg)](https://www.typescriptlang.org/)

核心能力：发布窗口管理、迭代与仓库关联、GitFlow 分支自动化、版本策略管理、冲突预检、编排执行。

> 📋 **新会话/新成员请先读：[`docs/project-ledger.md`](docs/project-ledger.md)** — 项目事实台账（当前阶段目标 / 已验证 / Top Priority / 关键证据索引）。

## 项目结构

```
release-hub/
├── backend/          # Spring Boot 后端（六边形架构）
├── frontend/         # Vue 3 + TypeScript + Vite 前端
└── docs/             # 设计文档、架构决策、工作流规范
```

## 快速开始

```bash
# 启动后端（Mock Git 适配器，本地开发安全）
cd backend && ./scripts/run.sh

# 启动后端（真实 GitLab 适配器，需要先配置 GitLab 连接）
cd backend && SPRING_PROFILES_ACTIVE=local,real ./scripts/run.sh

# 启动前端
cd frontend && pnpm install && pnpm dev
```

> **适配器模式**：默认使用 Mock 适配器（不访问真实 Git 平台）。叠加 `real` profile 启用真实 GitLab API 调用。详见 [部署指南](docs/deployment.md#23-git-平台适配器模式重要)。

## 文档

- [架构文档](docs/context/tech/architecture/) — 系统架构与技术栈
- [开发规范](docs/context/tech/conventions/) — 代码规范与测试策略
- [工作流](docs/workflow/) — AI 辅助开发工作流
- [API 契约](backend/docs/architecture.md) — REST API 概览

## 技术栈

| 端 | 技术 |
|---|------|
| 后端 | Java 21, Spring Boot 3.4, Maven 3.9+, PostgreSQL, Flyway |
| 前端 | Vue 3, TypeScript, Vite, pnpm |
| 测试 | JUnit 5, Testcontainers, ArchUnit, Vitest, Playwright |
