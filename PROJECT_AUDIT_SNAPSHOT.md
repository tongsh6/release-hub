# PROJECT AUDIT SNAPSHOT

## 1) 项目总览
- **工程名**: releasehub
- **根目录**: /Users/tongshuanglong/release-hub
- **Java 版本**: 21
- **Spring Boot 版本**: 3.2.8
- **Lombok 版本**: 1.18.36
- **模块列表**:
  - releasehub-common
  - releasehub-domain
  - releasehub-application
  - releasehub-infrastructure
  - releasehub-interfaces
  - releasehub-bootstrap

## 2) 目录树 (排除构建产物与配置)
```text
.
├── pom.xml
├── releasehub-application
│   ├── pom.xml
│   └── src
│       └── main
│           └── java
│               └── io
│                   └── releasehub
│                       └── application
│                           └── releasewindow
│                               ├── ReleaseWindowAppService.java
│                               └── ReleaseWindowRepository.java
├── releasehub-bootstrap
│   ├── pom.xml
│   └── src
│       ├── main
│       │   ├── java
│       │   │   └── io
│       │   │       └── releasehub
│       │   │           └── bootstrap
│       │   │               └── ReleaseHubApplication.java
│       │   └── resources
│       │       └── application.yml
│       └── test
│           └── java
│               └── io
│                   └── releasehub
│                       └── arch
│                           └── ArchitectureRulesTest.java
├── releasehub-common
│   ├── pom.xml
│   └── src
│       └── main
│           └── java
│               └── io
│                   └── releasehub
│                       └── common
│                           ├── exception
│                           │   └── BizException.java
│                           └── response
│                               └── ApiResponse.java
├── releasehub-domain
│   ├── pom.xml
│   └── src
│       ├── main
│       │   ├── java
│       │   │   └── io
│       │   │       └── releasehub
│       │   │           └── domain
│       │   │               └── releasewindow
│       │   │                   ├── ReleaseWindow.java
│       │   │                   ├── ReleaseWindowId.java
│       │   │                   └── ReleaseWindowStatus.java
│       └── test
│           └── java
│               └── io
│                   └── releasehub
│                       └── domain
│                           └── releasewindow
│                               └── ReleaseWindowTest.java
├── releasehub-infrastructure
│   ├── pom.xml
│   └── src
│       ├── main
│       │   ├── java
│       │   │   └── io
│       │   │       └── releasehub
│       │   │           └── infrastructure
│       │   │               └── persistence
│       │   │                   ├── PersistenceConfig.java
│       │   │                   ├── ReleaseWindowJpaEntity.java
│       │   │                   ├── ReleaseWindowJpaRepository.java
│       │   │                   └── ReleaseWindowRepositoryImpl.java
│       │   └── resources
│       │       └── db
│       │           └── migration
│       │               ├── V1__init.sql
│       │               └── V2__release_window_window_and_frozen.sql
├── releasehub-interfaces
│   ├── pom.xml
│   └── src
│       └── main
│           └── java
│               └── io
│                   └── releasehub
│                       ├── interfaces
│                       │   ├── api
│                       │   │   └── releasewindow
│                       │   │       ├── ConfigureReleaseWindowRequest.java
│                       │   │       ├── CreateReleaseWindowRequest.java
│                       │   │       ├── ReleaseWindowController.java
│                       │   │       └── ReleaseWindowView.java
│                       │   └── rest
│                       │       ├── GlobalExceptionHandler.java
│                       │       └── PingController.java
├── verify_rw.sh
└── verify_rw_v2.sh
```

## 3) 包分层核对
**io.releasehub.common..**
- `io.releasehub.common.exception`: `BizException`
- `io.releasehub.common.response`: `ApiResponse`

**io.releasehub.domain..**
- `io.releasehub.domain.releasewindow`: `ReleaseWindow`, `ReleaseWindowId`, `ReleaseWindowStatus`

**io.releasehub.application..**
- `io.releasehub.application.releasewindow`: `ReleaseWindowAppService`, `ReleaseWindowRepository`

**io.releasehub.infrastructure..**
- `io.releasehub.infrastructure.persistence`: `ReleaseWindowJpaEntity`, `ReleaseWindowJpaRepository`, `ReleaseWindowRepositoryImpl`, `PersistenceConfig`

**io.releasehub.interfaces..**
- `io.releasehub.interfaces.api.releasewindow`: `ReleaseWindowController`, `CreateReleaseWindowRequest`, `ReleaseWindowView`
- `io.releasehub.interfaces.rest`: `GlobalExceptionHandler`, `PingController`

**io.releasehub.bootstrap..**
- `io.releasehub.bootstrap`: `ReleaseHubApplication`

## 4) 启动类核对
**文件路径**: `releasehub-bootstrap/src/main/java/io/releasehub/bootstrap/ReleaseHubApplication.java`
```java
package io.releasehub.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.releasehub")
public class ReleaseHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReleaseHubApplication.class, args);
    }
}
```

**application.yml (关键片段)**
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:releasehub;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: none
  flyway:
    enabled: true
    locations: classpath:db/migration
```

## 5) POM 依赖矩阵核对
**父 POM (dependencyManagement)**
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- Internal Modules -->
        <dependency>
            <groupId>io.releasehub</groupId>
            <artifactId>releasehub-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <!-- ... other internal modules ... -->
    </dependencies>
</dependencyManagement>
```

**releasehub-common**
- `lombok` (provided)

**releasehub-domain**
- `lombok` (provided)
- `releasehub-common`
- `junit-jupiter` (test)

**releasehub-application**
- `lombok` (provided)
- `releasehub-domain`
- `releasehub-common`
- `spring-tx`
- `spring-context`

**releasehub-infrastructure**
- `lombok` (provided)
- `releasehub-application`
- `releasehub-common`
- `spring-boot-starter-data-jpa`
- `flyway-core`
- `h2` (runtime)

**releasehub-interfaces**
- `lombok` (provided)
- `releasehub-application`
- `releasehub-common`
- `spring-boot-starter-web`
- `spring-boot-starter-validation`

**releasehub-bootstrap**
- `releasehub-interfaces`
- `releasehub-infrastructure`
- `spring-boot-starter-actuator`
- `spring-boot-starter-test` (test)
- `archunit-junit5` (test)

## 6) ArchUnit 门禁核对
**文件路径**: `releasehub-bootstrap/src/test/java/io/releasehub/arch/ArchitectureRulesTest.java`
```java
@Test
void enforceLayeredArchitecture() {
    layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage("io.releasehub..")
            .layer("Common").definedBy("io.releasehub.common..")
            .layer("Domain").definedBy("io.releasehub.domain..")
            .layer("Application").definedBy("io.releasehub.application..")
            .layer("Infrastructure").definedBy("io.releasehub.infrastructure..")
            .layer("Interfaces").definedBy("io.releasehub.interfaces..")
            .layer("Bootstrap").definedBy("io.releasehub.bootstrap..")

            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Interfaces", "Bootstrap")
            .whereLayer("Application").mayOnlyAccessLayers("Domain", "Common")
            .whereLayer("Infrastructure").mayOnlyAccessLayers("Application", "Domain", "Common")
            .whereLayer("Interfaces").mayOnlyAccessLayers("Application", "Domain", "Common")
            .whereLayer("Bootstrap").mayOnlyAccessLayers("Interfaces", "Infrastructure", "Application", "Domain", "Common")
            .check(classes);
}

@Test
void domainLayerShouldBePure() {
    com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses().that().resideInAPackage("io.releasehub.domain..")
            .should().dependOnClassesThat(
                    com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("org.springframework..")
                    .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("jakarta.persistence.."))
                    .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("javax.persistence.."))
            )
            .as("Domain layer must not depend on Spring or Persistence frameworks")
            .check(classes);
}
```

## 7) ReleaseWindow V2 证据
**聚合根路径**: `releasehub-domain/src/main/java/io/releasehub/domain/releasewindow/ReleaseWindow.java`
```java
private Instant startAt;
private Instant endAt;
private boolean frozen;

public void configureWindow(Instant startAt, Instant endAt, Instant now) {
    if (!startAt.isBefore(endAt)) {
        throw new BizException("RW_INVALID_WINDOW", "StartAt must be strictly before EndAt");
    }
    this.startAt = startAt;
    this.endAt = endAt;
    this.updatedAt = now;
}

public void freeze(Instant now) {
    if (this.status != ReleaseWindowStatus.SUBMITTED) {
        throw new BizException("RW_INVALID_STATE", "Cannot freeze from state: " + this.status);
    }
    this.frozen = true;
    this.updatedAt = now;
}

public void release(Instant now) {
    if (this.frozen) {
        throw new BizException("RW_FROZEN", "Cannot release a frozen window");
    }
    if (this.startAt != null && this.endAt != null) {
        if (now.isBefore(this.startAt) || now.isAfter(this.endAt)) {
            throw new BizException("RW_OUT_OF_WINDOW", "Current time is outside the release window");
        }
    }
    this.status = ReleaseWindowStatus.RELEASED;
}
```

**Flyway V2 脚本**: `releasehub-infrastructure/src/main/resources/db/migration/V2__release_window_window_and_frozen.sql`
```sql
ALTER TABLE release_window ADD COLUMN start_at TIMESTAMP;
ALTER TABLE release_window ADD COLUMN end_at TIMESTAMP;
ALTER TABLE release_window ADD COLUMN frozen BOOLEAN DEFAULT FALSE NOT NULL;
```

**验证脚本**: `verify_rw_v2.sh` (存在，内容包含针对 Configure, Freeze, Release 的 curl 测试)

## 8) 构建与验证命令
1. `mvn -q clean test`
2. `mvn -pl releasehub-bootstrap spring-boot:run`
3. `bash verify_rw.sh`
4. `bash verify_rw_v2.sh`
