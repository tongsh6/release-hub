# PROJECT_AUDIT_SNAPSHOT

## 1. 项目总览
- **工程名**: releasehub
- **根目录名**: releasehub
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

## 2. 目录树 (关键路径)
```text
.
├── pom.xml
├── releasehub-application
│   ├── pom.xml
│   └── src
│       └── main
│           └── java
│               └── io/releasehub/application
│                   ├── project
│                   ├── releasewindow
│                   └── repo
├── releasehub-bootstrap
│   ├── pom.xml
│   └── src
│       ├── main
│       │   ├── java
│       │   │   └── io/releasehub/bootstrap
│       │   └── resources
│       │       └── application.yml
│       └── test
│           └── java
│               └── io/releasehub/arch
├── releasehub-common
│   ├── pom.xml
│   └── src
│       └── main
│           └── java
│               └── io/releasehub/common
├── releasehub-domain
│   ├── pom.xml
│   └── src
│       ├── main
│       │   └── java
│       │       └── io/releasehub/domain
│       │           ├── project
│       │           ├── releasewindow
│       │           └── repo
│       └── test
│           └── java
│               └── io/releasehub/domain
├── releasehub-infrastructure
│   ├── pom.xml
│   └── src
│       └── main
│           ├── java
│           │   └── io/releasehub/infrastructure
│           │       └── persistence
│           └── resources
│               └── db/migration
└── releasehub-interfaces
    ├── pom.xml
    └── src
        └── main
            └── java
                └── io/releasehub/interfaces
                    ├── api
                    └── rest
```

## 3. 包分层核对 (基于真实包名)

### io.releasehub.common..
- **exception**: BizException
- **response**: ApiResponse

### io.releasehub.domain..
- **project**: Project, ProjectId, ProjectStatus
- **releasewindow**: ReleaseWindow, ReleaseWindowId, ReleaseWindowStatus
- **repo**: CodeRepository, RepoId

### io.releasehub.application..
- **project**: ProjectAppService, ProjectRepository
- **releasewindow**: ReleaseWindowAppService, ReleaseWindowRepository, ReleaseWindowScopeAppService, ReleaseWindowScopeRepository
- **repo**: CodeRepositoryAppService, CodeRepositoryRepository

### io.releasehub.infrastructure..
- **persistence**: PersistenceConfig, ReleaseWindowJpaEntity, ReleaseWindowJpaRepository, ReleaseWindowRepositoryImpl

### io.releasehub.interfaces..
- **api.releasewindow**: ConfigureReleaseWindowRequest, CreateReleaseWindowRequest, ReleaseWindowController, ReleaseWindowView
- **rest**: GlobalExceptionHandler, PingController

### io.releasehub.bootstrap..
- **(root)**: ReleaseHubApplication

## 4. 启动类核对

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

**配置片段 (application.yml)**:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:releasehub;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1
  jpa:
    hibernate:
      ddl-auto: none
  flyway:
    enabled: true
    locations: classpath:db/migration
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

## 5. POM 依赖矩阵核对

### 父 POM (dependencyManagement)
- **Spring Boot**: `spring-boot-dependencies` (3.2.8)
- **Lombok**: `lombok` (1.18.36)
- **Modules**: common, domain, application, infrastructure, interfaces

### Lombok 配置
- **Dependency**: Provided scope in all modules.
- **Compiler**: `maven-compiler-plugin` with `annotationProcessorPaths` for `lombok`.

### 模块依赖摘要
- **releasehub-common**: lombok
- **releasehub-domain**: lombok, common, junit-jupiter
- **releasehub-application**: lombok, domain, common, spring-tx, spring-context
- **releasehub-infrastructure**: lombok, application, common, spring-boot-starter-data-jpa, flyway-core, h2
- **releasehub-interfaces**: lombok, application, common, spring-boot-starter-web, spring-boot-starter-validation
- **releasehub-bootstrap**: interfaces, infrastructure, spring-boot-starter-actuator, spring-boot-starter-test, archunit-junit5

## 6. ArchUnit 门禁核对

**文件路径**: `releasehub-bootstrap/src/test/java/io/releasehub/arch/ArchitectureRulesTest.java`

```java
package io.releasehub.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

public class ArchitectureRulesTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.releasehub");

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

                .whereLayer("Interfaces").mayOnlyBeAccessedByLayers("Bootstrap")
                .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Bootstrap")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Interfaces", "Infrastructure", "Bootstrap")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Interfaces")

                .whereLayer("Domain").mayOnlyAccessLayers("Common")
                .whereLayer("Application").mayOnlyAccessLayers("Domain", "Common")
                .whereLayer("Interfaces").mayOnlyAccessLayers("Application", "Domain", "Common")
                .whereLayer("Infrastructure").mayOnlyAccessLayers("Application", "Domain", "Common")
                .whereLayer("Bootstrap").mayOnlyAccessLayers("Interfaces", "Infrastructure")

                .check(classes);
    }
    
    // ... (Domain Purity & Common Restrictions tests omitted for brevity)
}
```

## 7. ReleaseWindow V2 证据

**聚合根路径**: `releasehub-domain/src/main/java/io/releasehub/domain/releasewindow/ReleaseWindow.java`

**关键代码摘录**:
```java
    private Instant startAt;
    private Instant endAt;
    private boolean frozen;

    public void configureWindow(Instant startAt, Instant endAt, Instant now) {
        if (startAt == null || endAt == null) throw new BizException("RW_INVALID_WINDOW", "StartAt and EndAt must not be null");
        if (!startAt.isBefore(endAt)) throw new BizException("RW_INVALID_WINDOW", "StartAt must be strictly before EndAt");
        this.startAt = startAt;
        this.endAt = endAt;
        this.updatedAt = now;
    }

    public void freeze(Instant now) {
        if (this.status != ReleaseWindowStatus.SUBMITTED) throw new BizException("RW_INVALID_STATE", "Cannot freeze from state: " + this.status);
        if (this.frozen) return;
        this.frozen = true;
        this.updatedAt = now;
    }

    public void unfreeze(Instant now) {
        if (!this.frozen) return;
        this.frozen = false;
        this.updatedAt = now;
    }

    public void release(Instant now) {
        // ...
        if (this.frozen) throw new BizException("RW_FROZEN", "Cannot release a frozen window");
        if (this.startAt != null && this.endAt != null) {
            if (now.isBefore(this.startAt) || now.isAfter(this.endAt)) {
                throw new BizException("RW_OUT_OF_WINDOW", "Current time is outside the release window");
            }
        }
        // ...
    }
```

**Flyway V2 脚本**: `releasehub-infrastructure/src/main/resources/db/migration/V2__release_window_window_and_frozen.sql`
```sql
ALTER TABLE release_window ADD COLUMN start_at TIMESTAMP;
ALTER TABLE release_window ADD COLUMN end_at TIMESTAMP;
ALTER TABLE release_window ADD COLUMN frozen BOOLEAN DEFAULT FALSE NOT NULL;
```

**验证脚本**: `verify_rw_v2.sh`
```bash
#!/bin/bash
# ... (setup omitted)

# 3. Configure Window
curl -s -X PUT $BASE_URL/$id/window \
  -H "Content-Type: application/json" \
  -d "{\"startAt\": \"$start_at\", \"endAt\": \"$end_at\"}" | python3 -m json.tool

# 4. Freeze
curl -s -X POST $BASE_URL/$id/freeze | python3 -m json.tool

# 5. Attempt Release (Expect Failure)
response=$(curl -s -X POST $BASE_URL/$id/release)
if echo "$response" | grep -q "RW_FROZEN"; then
    echo "SUCCESS: Release blocked by frozen state."
else
    echo "FAIL: Release should have been blocked."
    exit 1
fi

# 6. Unfreeze & 7. Release
curl -s -X POST $BASE_URL/$id/unfreeze | python3 -m json.tool
curl -s -X POST $BASE_URL/$id/release | python3 -m json.tool
```

## 8. 构建与验证命令

请按顺序执行以下命令进行验证：

1.  **单元测试与 ArchUnit 门禁**:
    ```bash
    mvn -q clean test
    ```
2.  **启动应用**:
    ```bash
    mvn -pl releasehub-bootstrap spring-boot:run
    ```
3.  **运行 V1 验证脚本**:
    ```bash
    bash verify_rw.sh
    ```
4.  **运行 V2 验证脚本**:
    ```bash
    bash verify_rw_v2.sh
    ```
