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
*   **实体设计**:
    *   所有领域实体 (Domain Entities) 必须继承 `io.releasehub.domain.base.BaseEntity`。
    *   使用充血模型，不仅仅是数据容器。
*   **错误处理**:
    *   业务错误必须使用 `io.releasehub.common.exception.BizException`。
    *   严禁直接抛出原始的 `RuntimeException`。
*   **API 响应**:
    *   所有 REST API 必须统一返回 `io.releasehub.common.response.ApiResponse<T>` 格式。

## 3. 数据库与持久化 (Database & Persistence)
*   **Schema 管理**: 所有数据库变更必须通过 Flyway 迁移脚本进行，位置在 `releasehub-infrastructure/src/main/resources/db/migration`。
*   **命名规范**: 迁移文件必须遵循 `V{Version}__{Description}.sql` 格式。
*   **JPA**: 仅允许在基础设施层使用 Spring Data JPA Repository。

## 4. 测试 (Testing)
*   **单元测试**: 领域逻辑和应用服务必须包含单元测试。
*   **集成测试**: 适配器和 API 流程必须包含集成测试（位于 `releasehub-bootstrap/src/test`）。
*   **架构测试**: 必须确保 `ArchitectureRulesTest` 通过，以验证分层边界未被破坏。

## 5. 安全 (Security)
*   **认证**: 使用现有的 `JwtAuthenticationFilter` 机制。
*   **密码**: 必须始终使用 `BCryptPasswordAdapter` 进行哈希处理。

## 6. API 文档 (API Documentation)
*   **OpenAPI**: 控制器 (Controller) 必须维护 Swagger 注解 (`@Operation`, `@Tag`)。
