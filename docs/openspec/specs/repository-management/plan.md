# Implementation Plan: 代码仓库管理

**Branch**: `feature/repository-management` | **Date**: 2025-12-27 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/repository-management/spec.md`

## Summary

实现代码仓库的全生命周期管理，包括创建、查询、更新、删除以及与 GitLab 的元数据同步。核心是 `CodeRepository` 聚合根以及配套的 `GitLabPort` 适配器。

## Technical Context

**Language/Version**: Java 21 (Spring Boot 3.2.8), TypeScript (Vue 3)
**Primary Dependencies**: Spring Data JPA, Axios, Element Plus
**Storage**: MySQL/H2 (JPA)
**Testing**: JUnit 5, Mockito

## Project Structure

### Documentation (this feature)

```text
specs/repository-management/
├── plan.md              # This file
├── spec.md              # Feature Specification
└── tasks.md             # Implementation Tasks
```

### Source Code (repository root)

```text
# Backend (releasehub-domain)
io.releasehub.domain.repo/
├── CodeRepository.java        # Aggregate Root
├── RepoId.java                # Value Object
├── CodeRepositoryPort.java    # Repository Interface
└── GitLabPort.java            # ACL Interface (Anti-Corruption Layer)

# Backend (releasehub-application)
io.releasehub.application.repo/
├── CodeRepositoryAppService.java # Application Service
└── dto/                          # DTOs (BranchSummary, GateSummary)

# Backend (releasehub-infrastructure)
io.releasehub.infrastructure.repo/
├── CodeRepositoryJpaRepository.java # JPA DAO
└── CodeRepositoryJpaAdapter.java    # Port Implementation

io.releasehub.infrastructure.gitlab/
├── GitLabAdapter.java         # GitLab API Client Implementation

# Frontend (release-hub-web)
src/views/repository/
├── RepositoryList.vue
├── RepositoryDetail.vue
├── RepositoryDrawer.vue
└── RepositoryEdit.vue
```

## Structure Decision

采用典型的 DDD 分层架构 + Clean Architecture：
- **Domain**: 包含业务逻辑和实体状态验证。
- **Application**: 编排用例，协调 Domain 和 Infrastructure。
- **Infrastructure**: 实现具体的持久化和外部系统（GitLab）调用。
- **Interface**: 处理 HTTP 请求。

## Gap Analysis (Current vs Spec)

| Component | Status | Missing / Refinement Needed |
|-----------|--------|-----------------------------|
| **Domain** | ✅ Exist | `validateName` 等校验逻辑已存在，需确保覆盖所有 Spec 边界条件。 |
| **App Service** | ⚠️ Partial | `sync` 同步逻辑可能仅为骨架，需要完善真实 GitLab 调用。 |
| **Infra (JPA)** | ✅ Exist | 已迁移至 JPA，需验证查询效率。 |
| **Infra (GitLab)**| ⚠️ Partial | `GitLabAdapter` 可能尚未完全实现 `getBranchSummary` 等逻辑。 |
| **Frontend** | ⚠️ Partial | 详情页展示了 Summary，但数据可能未联调通畅；缺少手动“同步”按钮。 |
