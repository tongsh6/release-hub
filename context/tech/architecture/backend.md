# Backend Project Structure Report

生成时间（UTC）：2025-12-18T20:33:32Z
仓库根目录：`/Users/tongshuanglong/release-hub`

## 1. 仓库目录树（过滤噪音目录）

```
.
、
releasehub-application
releasehub-application/.flattened-pom.xml
releasehub-application/pom.xml
releasehub-application/src
releasehub-application/src/main
releasehub-application/src/main/java
releasehub-application/src/main/java/io
releasehub-application/src/main/java/io/releasehub
releasehub-bootstrap
releasehub-bootstrap/.flattened-pom.xml
releasehub-bootstrap/pom.xml
releasehub-bootstrap/src
releasehub-bootstrap/src/main
releasehub-bootstrap/src/main/java
releasehub-bootstrap/src/main/java/io
releasehub-bootstrap/src/main/java/io/releasehub
releasehub-bootstrap/src/main/resources
releasehub-bootstrap/src/main/resources/application.yml
releasehub-bootstrap/src/main/resources/db
releasehub-bootstrap/src/main/resources/db/migration
releasehub-bootstrap/src/test
releasehub-bootstrap/src/test/java
releasehub-bootstrap/src/test/java/io
releasehub-bootstrap/src/test/java/io/releasehub
releasehub-common
releasehub-common/.flattened-pom.xml
releasehub-common/pom.xml
releasehub-common/src
releasehub-common/src/main
releasehub-common/src/main/java
releasehub-common/src/main/java/io
releasehub-common/src/main/java/io/releasehub
releasehub-domain
releasehub-domain/.flattened-pom.xml
releasehub-domain/pom.xml
releasehub-domain/src
releasehub-domain/src/main
releasehub-domain/src/main/java
releasehub-domain/src/main/java/io
releasehub-domain/src/main/java/io/releasehub
releasehub-domain/src/test
releasehub-domain/src/test/java
releasehub-domain/src/test/java/io
releasehub-domain/src/test/java/io/releasehub
releasehub-infrastructure
releasehub-infrastructure/.flattened-pom.xml
releasehub-infrastructure/pom.xml
releasehub-infrastructure/src
releasehub-infrastructure/src/main
releasehub-infrastructure/src/main/java
releasehub-infrastructure/src/main/java/io
releasehub-infrastructure/src/main/java/io/releasehub
releasehub-infrastructure/src/main/resources
releasehub-infrastructure/src/main/resources/db
releasehub-infrastructure/src/main/resources/db/migration
releasehub-interfaces
releasehub-interfaces/.flattened-pom.xml
releasehub-interfaces/pom.xml
releasehub-interfaces/src
releasehub-interfaces/src/main
releasehub-interfaces/src/main/java
releasehub-interfaces/src/main/java/io
releasehub-interfaces/src/main/java/io/releasehub

```

## 2. Maven 模块

```
[pom.xml] 存在

modules：
- releasehub-common
- releasehub-domain
- releasehub-application
- releasehub-infrastructure
- releasehub-interfaces
- releasehub-bootstrap


## 3. Java 源码类清单（main）

```
./releasehub-application/src/main/java/io/releasehub/application/auth/AuthAppService.java
./releasehub-application/src/main/java/io/releasehub/application/auth/PasswordService.java
./releasehub-application/src/main/java/io/releasehub/application/auth/TokenInfo.java
./releasehub-application/src/main/java/io/releasehub/application/auth/TokenProvider.java
./releasehub-application/src/main/java/io/releasehub/application/project/ProjectAppService.java
./releasehub-application/src/main/java/io/releasehub/application/project/ProjectPort.java
./releasehub-application/src/main/java/io/releasehub/application/releasewindow/ReleaseWindowAppService.java
./releasehub-application/src/main/java/io/releasehub/application/releasewindow/ReleaseWindowPort.java
./releasehub-application/src/main/java/io/releasehub/application/releasewindow/ReleaseWindowScopeAppService.java
./releasehub-application/src/main/java/io/releasehub/application/releasewindow/ReleaseWindowScopePort.java
./releasehub-application/src/main/java/io/releasehub/application/repo/CodeRepositoryAppService.java
./releasehub-application/src/main/java/io/releasehub/application/repo/CodeRepositoryPort.java
./releasehub-application/src/main/java/io/releasehub/application/user/UserPort.java
./releasehub-bootstrap/src/main/java/io/releasehub/bootstrap/config/OpenApiConfig.java
./releasehub-bootstrap/src/main/java/io/releasehub/bootstrap/ReleaseHubApplication.java
./releasehub-common/src/main/java/io/releasehub/common/exception/BizException.java
./releasehub-common/src/main/java/io/releasehub/common/response/ApiResponse.java
./releasehub-domain/src/main/java/io/releasehub/domain/project/Project.java
./releasehub-domain/src/main/java/io/releasehub/domain/project/ProjectId.java
./releasehub-domain/src/main/java/io/releasehub/domain/project/ProjectStatus.java
./releasehub-domain/src/main/java/io/releasehub/domain/releasewindow/ReleaseWindow.java
./releasehub-domain/src/main/java/io/releasehub/domain/releasewindow/ReleaseWindowId.java
./releasehub-domain/src/main/java/io/releasehub/domain/releasewindow/ReleaseWindowStatus.java
./releasehub-domain/src/main/java/io/releasehub/domain/repo/CodeRepository.java
./releasehub-domain/src/main/java/io/releasehub/domain/repo/RepoId.java
./releasehub-domain/src/main/java/io/releasehub/domain/user/User.java
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/config/PersistenceConfig.java
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/project/ProjectPersistenceAdapter.java
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/releasewindow/ReleaseWindowJpaEntity.java
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/releasewindow/ReleaseWindowJpaRepository.java
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/releasewindow/ReleaseWindowPersistenceAdapter.java
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/repo/CodeRepositoryPersistenceAdapter.java
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/scope/ReleaseWindowScopePersistenceAdapter.java
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/user/UserJpaEntity.java
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/user/UserJpaRepository.java
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/user/UserPersistenceAdapter.java
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/security/BCryptPasswordService.java
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/security/JwtAuthenticationFilter.java
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/security/JwtService.java
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/security/SecurityConfig.java
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/security/UserDetailsServiceImpl.java
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/seed/DataSeeder.java
./releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/ConfigureReleaseWindowRequest.java
./releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/CreateReleaseWindowRequest.java
./releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/ReleaseWindowController.java
./releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/ReleaseWindowView.java
./releasehub-interfaces/src/main/java/io/releasehub/interfaces/auth/AuthController.java
./releasehub-interfaces/src/main/java/io/releasehub/interfaces/rest/GlobalExceptionHandler.java
./releasehub-interfaces/src/main/java/io/releasehub/interfaces/rest/PingController.java
```

## 4. Java 测试类清单（test）

```
./releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/api/AuthApiTest.java
./releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/arch/ArchitectureRulesTest.java
./releasehub-domain/src/test/java/io/releasehub/domain/project/ProjectTest.java
./releasehub-domain/src/test/java/io/releasehub/domain/releasewindow/ReleaseWindowTest.java
./releasehub-domain/src/test/java/io/releasehub/domain/repo/CodeRepositoryTest.java
```

## 5. Spring 关键注解/配置扫描（入口/JPA/Security/Flyway）

```
./releasehub-bootstrap/src/main/java/io/releasehub/bootstrap/ReleaseHubApplication.java:6:@SpringBootApplication(scanBasePackages = "io.releasehub")
./releasehub-bootstrap/src/main/java/io/releasehub/bootstrap/config/OpenApiConfig.java:8:@Configuration
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/seed/DataSeeder.java:19:@Configuration
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/security/SecurityConfig.java:25:@Configuration
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/security/SecurityConfig.java:26:@EnableWebSecurity
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/config/PersistenceConfig.java:10:@Configuration
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/config/PersistenceConfig.java:11:@EnableJpaRepositories(basePackages = "io.releasehub.infrastructure.persistence")
./releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/config/PersistenceConfig.java:12:@EntityScan(basePackages = "io.releasehub.infrastructure.persistence")
```

## 6. 分层目录分布概览（domain/application/interfaces/infra/persistence/bootstrap）

```
releasehub-application/src/main/java/io/releasehub/application/auth/AuthAppService.java
releasehub-application/src/main/java/io/releasehub/application/auth/PasswordService.java
releasehub-application/src/main/java/io/releasehub/application/auth/TokenInfo.java
releasehub-application/src/main/java/io/releasehub/application/auth/TokenProvider.java
releasehub-application/src/main/java/io/releasehub/application/project/ProjectAppService.java
releasehub-application/src/main/java/io/releasehub/application/project/ProjectPort.java
releasehub-application/src/main/java/io/releasehub/application/releasewindow/ReleaseWindowAppService.java
releasehub-application/src/main/java/io/releasehub/application/releasewindow/ReleaseWindowPort.java
releasehub-application/src/main/java/io/releasehub/application/releasewindow/ReleaseWindowScopeAppService.java
releasehub-application/src/main/java/io/releasehub/application/releasewindow/ReleaseWindowScopePort.java
releasehub-application/src/main/java/io/releasehub/application/repo/CodeRepositoryAppService.java
releasehub-application/src/main/java/io/releasehub/application/repo/CodeRepositoryPort.java
releasehub-application/src/main/java/io/releasehub/application/user/UserPort.java
releasehub-bootstrap/src/main/java/io/releasehub/bootstrap/config/OpenApiConfig.java
releasehub-bootstrap/src/main/java/io/releasehub/bootstrap/ReleaseHubApplication.java
releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/api/AuthApiTest.java
releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/arch/ArchitectureRulesTest.java
releasehub-domain/src/main/java/io/releasehub/domain/project/Project.java
releasehub-domain/src/main/java/io/releasehub/domain/project/ProjectId.java
releasehub-domain/src/main/java/io/releasehub/domain/project/ProjectStatus.java
releasehub-domain/src/main/java/io/releasehub/domain/releasewindow/ReleaseWindow.java
releasehub-domain/src/main/java/io/releasehub/domain/releasewindow/ReleaseWindowId.java
releasehub-domain/src/main/java/io/releasehub/domain/releasewindow/ReleaseWindowStatus.java
releasehub-domain/src/main/java/io/releasehub/domain/repo/CodeRepository.java
releasehub-domain/src/main/java/io/releasehub/domain/repo/RepoId.java
releasehub-domain/src/main/java/io/releasehub/domain/user/User.java
releasehub-domain/src/test/java/io/releasehub/domain/project/ProjectTest.java
releasehub-domain/src/test/java/io/releasehub/domain/releasewindow/ReleaseWindowTest.java
releasehub-domain/src/test/java/io/releasehub/domain/repo/CodeRepositoryTest.java
releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/config/PersistenceConfig.java
releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/project/ProjectPersistenceAdapter.java
releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/releasewindow/ReleaseWindowJpaEntity.java
releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/releasewindow/ReleaseWindowJpaRepository.java
releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/releasewindow/ReleaseWindowPersistenceAdapter.java
releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/repo/CodeRepositoryPersistenceAdapter.java
releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/scope/ReleaseWindowScopePersistenceAdapter.java
releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/user/UserJpaEntity.java
releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/user/UserJpaRepository.java
releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/user/UserPersistenceAdapter.java
releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/security/BCryptPasswordService.java
releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/security/JwtAuthenticationFilter.java
releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/security/JwtService.java
releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/security/SecurityConfig.java
releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/security/UserDetailsServiceImpl.java
releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/seed/DataSeeder.java
releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/ConfigureReleaseWindowRequest.java
releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/CreateReleaseWindowRequest.java
releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/ReleaseWindowController.java
releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/ReleaseWindowView.java
releasehub-interfaces/src/main/java/io/releasehub/interfaces/auth/AuthController.java
releasehub-interfaces/src/main/java/io/releasehub/interfaces/rest/GlobalExceptionHandler.java
releasehub-interfaces/src/main/java/io/releasehub/interfaces/rest/PingController.java
```

## 7. Flyway migration 位置扫描（避免重复来源）

```
releasehub-infrastructure/src/main/resources/db/migration/V1__init.sql
releasehub-infrastructure/src/main/resources/db/migration/V2__release_window_window_and_frozen.sql
releasehub-infrastructure/src/main/resources/db/migration/V3__create_users_table.sql
```

