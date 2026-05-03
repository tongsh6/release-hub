# Project Context

## Purpose
ReleaseHub 聚焦多项目/多仓库的发布节奏治理，提供统一的发布窗口（含时间窗、状态、冻结）、项目与代码仓拓扑、分支命名规则、基于 GitLab 的仓库统计、迭代与窗口关联，以及发布运行记录，帮助团队降低跨仓发布的认知和执行成本。

## Tech Stack
- 仓库结构：后端 `release-hub`（Maven 多模块），前端 `release-hub-web`（Vue 应用）。
- 后端：Java 21，Spring Boot 3.2.8，模块 common/domain/application/infrastructure/interfaces/bootstrap；Spring Data JPA + Flyway 迁移（`releasehub-infrastructure/src/main/resources/db/migration`）；Spring Security + JWT 过滤器；springdoc OpenAPI；Lombok。
- 数据库：`application-local.yml` 连接 PostgreSQL（schema `release_hub`，`ddl-auto=update`，本地默认关闭 Flyway）；`application-test.yml` 使用 H2 PostgreSQL 模式并启用 Flyway；`application-prd.yml` 使用 PostgreSQL，Flyway 校验模式。
- API/文档：REST 路由前缀 `/api/v1`；本地/测试开启 Swagger UI，生产关闭。
- 前端：Vue 3 + TypeScript + Vite（rolldown-vite）+ Element Plus，Pinia，Vue Router，Vue I18n，Axios；pnpm 管理；openapi-typescript 生成 `src/api/schema.d.ts`；plop CRUD 模板；Vitest 单测。
- 工具与质量：ArchUnit + Maven Enforcer 强制分层，JUnit 5 + MockMvc，ESLint + Prettier + TypeScript 类型检查，`scripts/i18n-check.js` 校验多语言。

## Project Conventions

### Code Style
- 后端：Domain 层保持无框架；Port 接口在 `application`，Adapter 在 `infrastructure`；控制器在 `releasehub-interfaces`，统一返回 `ApiResponse`/`ApiPageResponse`，路径 `/api/v1/**`，使用 `jakarta.validation` 与 Swagger 注解；数据类默认使用 Lombok。
- 前端：ESLint flat（Vue/TS/Prettier），允许单词组件名与适度 `any`，未使用参数允许 `_` 前缀；优选 `<script setup>` + Element Plus；API 调用在 `src/api/http.ts`，路径使用 `/v1/...`，`VITE_API_BASE_URL` 需包含 `/api` 前缀；i18n 消息在 `src/i18n`，语言持久化 `RH_LOCALE`；登录 token 保存在 `RH_TOKEN`；权限通过 `v-perm` 指令和 `hasPerm`。

### Architecture Patterns
- DDD 风格分层单体，由 ArchUnit + Maven Enforcer 约束：domain 仅依赖 common；application 依赖 domain/common；infrastructure 与 interfaces 依赖 application/common；bootstrap 依赖 interfaces/infrastructure。主要 Port/Adapter 覆盖发布窗口、迭代、仓库、分组、运行记录、认证与系统设置。
- 持久化：Spring Data JPA，迁移脚本在 `releasehub-infrastructure/src/main/resources/db/migration`；本地 Postgres `ddl-auto=update`，测试/生产依赖 Flyway。
- 安全：无状态 Spring Security，`JwtAuthenticationFilter` 置于 `UsernamePasswordAuthenticationFilter` 之前；登录 `POST /api/v1/auth/login`；CORS 来源由 `cors.allowedOrigins` 配置；各环境 JWT Secret 独立。
- GitLab 集成：`GitLabAdapter` 读取 `SettingsPort` 的 GitLab 设置（内存存储），调用 REST API 统计分支/MR，并基于正则做分支规范校验；未配置时分支/MR信息使用空或占位返回。
- 数据种子：`releasehub.seed.enabled=true` 时通过 `DataSeeder` 清理 `code_repository` 并创建固定管理员 `admin/admin`；生产默认关闭。
- 前端结构：路由按功能在 `src/router/modules`，通用 CRUD 组件/组合式函数（`useListPage`、`SearchForm`、`DataTable`），API 模块在 `src/api/modules`。

### Testing Strategy
- 后端：`mvn -q clean test` 或 `mvn -pl releasehub-bootstrap test` 运行领域单测、ArchUnit 校验、MockMvc 集成测试（如 `AuthApiTest`），使用 H2 + Flyway 的测试配置。手工脚本 `verify_rw*.sh` 需在 `releasehub-bootstrap` 启动后验证发布窗口流程。
- 前端：`pnpm lint`、`pnpm typecheck`、`pnpm test`、`pnpm i18n:lint`；后端 OpenAPI 变化时运行 `pnpm gen:api` 更新类型。
- 自动校验：每个变更完成后在 `release-hub-web` 自动执行 `pnpm lint && pnpm typecheck` 并记录结果（含警告/错误），作为归档前必做项。

### Git Workflow
- Not formally documented; default to feature branches with PR reviews before merging to the main line. No automated Git/CI integrations implemented yet (interfaces are planned for later).

## Domain Context
- 发布窗口（ReleaseWindow）：状态流转 Draft → Published → Released → Closed；需先配置 start/end；支持冻结/解冻、发布、关闭；窗口可关联迭代并暴露编排/计划接口。
- 迭代与关联（Iteration/WindowIteration）：迭代以键标识并包含仓库集合；关联记录附带 attach 时间。
- 项目与代码仓（Project/CodeRepository）：项目登记 + GitLab 仓库（ID、cloneUrl、默认分支、是否 mono-repo），可同步分支/MR 统计。
- 分组（Group）：按 code 形成父子层级，用于组织项目/发布。
- 运行记录（Run）：记录发布运行与步骤，保留历史。
- 认证与设置（Auth/Settings）：基础用户模型与种子管理员；GitLab/命名/阻塞策略设置经 `SettingsPort` 内存存储。

## Important Constraints
- 分层约束：ArchUnit/Maven Enforcer 禁止 domain 依赖 Spring/JPA/Hibernate；infrastructure 禁止依赖 interfaces；bootstrap 禁止直接依赖 domain/application/common。
- 发布窗口校验：key/name 非空且长度限制；startAt 必须早于 endAt；冻结后禁止配置；仅 Draft 可发布且需先配置时间；仅 Published 可 release；仅 Released 可 close。
- 代码仓校验：必须提供 projectId、gitlabProjectId、cloneUrl、默认分支且长度受限；同步统计会覆盖计数与时间戳。
- 分组校验：parentCode 不可等于自身且长度受限；迭代键非空并维护仓库集合。
- 种子与配置：`releasehub.seed.enabled` 控制清表与管理员注入；GitLab 设置仅存内存，重启丢失，调用前需先设置。
- 跨域/文档/鉴权：各 profile 独立配置 CORS、Swagger、JWT Secret；前端 `VITE_API_BASE_URL` 需含 `/api` 以命中 `/api/v1` 路由。

## External Dependencies
- 后端库：Spring Boot starters（web/validation/data-jpa/security/actuator）、Spring Security + JWT、Flyway、H2（测试）、Lombok、ArchUnit、RestTemplate（GitLab 调用）。
- 前端库：Element Plus、Pinia、Vue Router、Vue I18n、Axios、Day.js、openapi-typescript、plop、Vitest、ESLint/Prettier。
- 外部服务：GitLab REST API（分支/MR），token 通过系统设置注入；前端以 `VITE_API_BASE_URL` 访问后端。
