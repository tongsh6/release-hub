# ReleaseHub Project Context

## 项目定位

ReleaseHub 是基于 DDD 的多仓库发布协调平台，管理 Release Windows、版本策略、分支规则和自动化版本更新。

## 架构分层（模块化单体）

- `releasehub-domain/`：聚合根、实体、值对象（无外部依赖）
- `releasehub-application/`：用例编排、事务边界（Port 接口）
- `releasehub-infrastructure/`：JPA 实现、适配器
- `releasehub-interfaces/`：REST 控制器、DTO
- `releasehub-bootstrap/`：Spring Boot 入口

## 关键规则

- Domain 层禁止依赖其他层
- Application 层定义 Port 接口，Infrastructure 层实现
- 领域模型优先使用工厂方法（如 `createDraft()`）与重建（`rehydrate()`）模式
- 采用 TDD：RED → GREEN → REFACTOR

## 前端约束（Vue 3 + TypeScript）

- 页面状态使用 `reactive`/`ref`，避免 Pinia
- API 类型通过 `pnpm gen:api` 生成
- 代理：`/api/*` → `http://localhost:8080`

## 文档入口

- 项目总规范：`AGENTS.md`
- 知识库索引：`context/INDEX.md`
- OpenSpec 工作流：`openspec/AGENTS.md`
- AI 工程化配置：`.ai/README.md`
