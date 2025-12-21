# ReleaseHub 项目开发规范

## 1. 架构原则 (Architectural Principles)
*   **六边形架构 (Hexagonal Architecture)**: 所有代码必须严格遵循六边形架构模式。
    *   **领域层 (`releasehub-domain`)**: 纯 Java 业务逻辑。严禁依赖任何框架（如 Spring）、数据库或外部 API。
    *   **应用层 (`releasehub-application`)**: 编排用例 (Use Cases)。仅依赖领域层 (Domain) 和端口 (Ports)。
    *   **基础设施层 (`releasehub-infrastructure`)**: 实现端口 (Adapters)。包含所有技术实现细节（数据库、外部 API、框架配置）。
    *   **接口层 (`releasehub-interfaces`)**: 处理外部请求（REST Controllers）。
*   **依赖规则**: 依赖关系必须指向内部。领域层不依赖任何层。基础设施层依赖应用层和领域层。

## 2. 代码规范 (Coding Standards)
*   **Java 版本**: Java 21。
*   **Lombok**: 必须使用 Lombok 简化样板代码 (`@Data`, `@RequiredArgsConstructor`, `@Builder`)。
*   **领域类**:
    *   使用充血模型，不仅仅是数据容器。
*   **错误处理**:
    *   业务错误必须使用 `io.releasehub.common.exception.BizException`。
    *   严禁直接抛出原始的 `RuntimeException`。
*   **API 响应**:
    *   所有 REST API 必须统一返回 `io.releasehub.common.response.ApiResponse<T>` 格式。

## 3. 数据库与持久化 (Database & Persistence)
<!-- *   **Schema 管理**: 所有数据库变更必须通过 Flyway 迁移脚本进行，位置在 `releasehub-infrastructure/src/main/resources/db/migration`。
*   **命名规范**: 迁移文件必须遵循 `V{Version}__{Description}.sql` 格式。 -->
*   **JPA**: 仅允许在基础设施层使用 Spring Data JPA Repository。
*   **JPA 实体**: JPA 实体位于 `releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/<bounded-context>` 目录（按业务上下文归档）。
*   **JPA 实体**: 统一复用领域基类 `BaseEntity<ID>` 的审计与版本能力；JPA 审计字段需与领域基类保持一致映射。
*   **表/列命名**: 数据库表与列统一使用 `snake_case`；不使用 `T_` 前缀；必要时通过 `@Table/@Column` 显式映射。
*   **数据库**: 必须使用 PostgreSQL。

### 3.1 迁移与建表策略（分环境）
*   **local（本地开发）**: 启用 JPA 自动建表/变更（`spring.jpa.hibernate.ddl-auto=update`），关闭 Flyway（`spring.flyway.enabled=false`），默认 schema 为 `release_hub`（`hibernate.default_schema`），启用 `hbm2ddl.create_namespaces=true`。
*   **test / unitTest（测试）**: 使用 H2 内存库；关闭 JPA DDL（`ddl-auto=none`）；开启 Flyway（`spring.flyway.enabled=true`）以校验迁移。
*   **prd（生产）**: 严格通过 Flyway 管理结构变更（`enabled=true`，`locations=classpath:db/migration`），JPA 仅校验结构（`ddl-auto=validate`）。
*   **迁移命名规范**: 迁移文件必须遵循 `V{Version}__{Description}.sql` 格式。

## 4. 测试 (Testing)
*   **单元测试**: 领域逻辑和应用服务必须包含单元测试。
*   **集成测试**: 适配器和 API 流程必须包含集成测试（位于 `releasehub-bootstrap/src/test`）。
*   **架构测试**: 必须确保 `ArchitectureRulesTest` 通过，以验证分层边界未被破坏。
*   **测试配置**: 测试 Profile 使用 `test`/`unitTest`；不要在测试中启用生产配置或种子污染生产数据。

## 5. 安全 (Security)
*   **认证**: 使用现有的 `JwtAuthenticationFilter` 机制。
*   **密码**: 必须始终使用 `BCryptPasswordAdapter` 进行哈希处理。
*   **开放端点**: 允许匿名访问 `/api/v1/auth/login`、`/actuator/health`、`/v3/api-docs/**`、`/swagger-ui/**`；其余端点需认证。
*   **安全要求**: 无状态会话，禁用 CSRF；禁止记录/输出敏感信息（token、密码）。

## 6. API 文档 (API Documentation)
*   **OpenAPI**: 控制器 (Controller) 必须维护 Swagger 注解 (`@Operation`, `@Tag`)。
*   **springdoc 路径**: 统一 `/v3/api-docs` 与 `/swagger-ui.html`；生产可关闭 UI。

## 7. 接口层规范 (Interfaces)
*   **路径风格**: 资源根使用复数；分页端点统一路径 `.../paged`（不使用前导下划线）。
*   **返回值**: 统一 `ApiResponse<T>`；分页统一使用 `ApiPageResponse<List<T>>`。
*   **DTO 校验**: 输入 DTO 使用 Jakarta Validation 注解并在控制器使用 `@Valid`。

## 8. 模块命名与适配器 (Module & Adapters)
*   **Port/Adapter 约定**: `application` 层接口以 `*Port`/`*Gateway` 命名；`infrastructure` 层实现以 `*Adapter`/`*PersistenceAdapter` 命名，并通过 ArchUnit 校验。

## 9. 配置与运维 (Config & Ops)
*   **配置文件**: 按环境维护 `application-*.yml`；默认激活 `local` 开发配置。
*   **密钥管理**: JWT 密钥必须足够强度并通过环境变量或安全配置注入；禁止硬编码在代码。
*   **种子数据**: 仅在开发/测试环境启用种子数据，避免生产数据污染。
