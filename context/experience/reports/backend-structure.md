# ReleaseHub Backend Structure Report
Generated on 2025年12月19日 星期五 02时51分33秒 CST

## 1. Module Directory Tree
```
.
  releasehub-application
    src
      main
        java
          io
            releasehub
  releasehub-bootstrap
    src
      main
        java
          io
            releasehub
        resources
          application.yml
          db
            migration
      test
        java
          io
            releasehub
  releasehub-common
    src
      main
        java
          io
            releasehub
  releasehub-domain
    src
      main
        java
          io
            releasehub
      test
        java
          io
            releasehub
  releasehub-infrastructure
    src
      main
        java
          io
            releasehub
        resources
          db
            migration
  releasehub-interfaces
    src
      main
        java
          io
            releasehub
  run.log
  run.sh
  verify_rw_v2.sh
  verify_rw.sh
```

## 2. Java File List

### Module: releasehub-domain
| Relative Path | Package Declaration |
|---|---|
| releasehub-domain/src/main/java/io/releasehub/domain/project/Project.java | io.releasehub.domain.project |
| releasehub-domain/src/main/java/io/releasehub/domain/project/ProjectId.java | io.releasehub.domain.project |
| releasehub-domain/src/main/java/io/releasehub/domain/project/ProjectStatus.java | io.releasehub.domain.project |
| releasehub-domain/src/main/java/io/releasehub/domain/releasewindow/ReleaseWindow.java | io.releasehub.domain.releasewindow |
| releasehub-domain/src/main/java/io/releasehub/domain/releasewindow/ReleaseWindowId.java | io.releasehub.domain.releasewindow |
| releasehub-domain/src/main/java/io/releasehub/domain/releasewindow/ReleaseWindowStatus.java | io.releasehub.domain.releasewindow |
| releasehub-domain/src/main/java/io/releasehub/domain/repo/CodeRepository.java | io.releasehub.domain.repo |
| releasehub-domain/src/main/java/io/releasehub/domain/repo/RepoId.java | io.releasehub.domain.repo |
| releasehub-domain/src/main/java/io/releasehub/domain/user/User.java | io.releasehub.domain.user |
| releasehub-domain/src/main/java/io/releasehub/domain/user/UserRepository.java | io.releasehub.domain.user |
| releasehub-domain/src/test/java/io/releasehub/domain/project/ProjectTest.java | io.releasehub.domain.project |
| releasehub-domain/src/test/java/io/releasehub/domain/releasewindow/ReleaseWindowTest.java | io.releasehub.domain.releasewindow |
| releasehub-domain/src/test/java/io/releasehub/domain/repo/CodeRepositoryTest.java | io.releasehub.domain.repo |

### Module: releasehub-application
| Relative Path | Package Declaration |
|---|---|
| releasehub-application/src/main/java/io/releasehub/application/auth/AuthAppService.java | io.releasehub.application.auth |
| releasehub-application/src/main/java/io/releasehub/application/auth/PasswordService.java | io.releasehub.application.auth |
| releasehub-application/src/main/java/io/releasehub/application/auth/TokenInfo.java | io.releasehub.application.auth |
| releasehub-application/src/main/java/io/releasehub/application/auth/TokenProvider.java | io.releasehub.application.auth |
| releasehub-application/src/main/java/io/releasehub/application/project/ProjectAppService.java | io.releasehub.application.project |
| releasehub-application/src/main/java/io/releasehub/application/project/ProjectRepository.java | io.releasehub.application.project |
| releasehub-application/src/main/java/io/releasehub/application/releasewindow/ReleaseWindowAppService.java | io.releasehub.application.releasewindow |
| releasehub-application/src/main/java/io/releasehub/application/releasewindow/ReleaseWindowRepository.java | io.releasehub.application.releasewindow |
| releasehub-application/src/main/java/io/releasehub/application/releasewindow/ReleaseWindowScopeAppService.java | io.releasehub.application.releasewindow |
| releasehub-application/src/main/java/io/releasehub/application/releasewindow/ReleaseWindowScopeRepository.java | io.releasehub.application.releasewindow |
| releasehub-application/src/main/java/io/releasehub/application/repo/CodeRepositoryAppService.java | io.releasehub.application.repo |
| releasehub-application/src/main/java/io/releasehub/application/repo/CodeRepositoryRepository.java | io.releasehub.application.repo |

### Module: releasehub-infrastructure
| Relative Path | Package Declaration |
|---|---|
| releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/CodeRepositoryRepositoryImpl.java | io.releasehub.infrastructure.persistence |
| releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/PersistenceConfig.java | io.releasehub.infrastructure.persistence |
| releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/ProjectRepositoryImpl.java | io.releasehub.infrastructure.persistence |
| releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/ReleaseWindowJpaEntity.java | io.releasehub.infrastructure.persistence |
| releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/ReleaseWindowJpaRepository.java | io.releasehub.infrastructure.persistence |
| releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/ReleaseWindowRepositoryImpl.java | io.releasehub.infrastructure.persistence |
| releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/persistence/ReleaseWindowScopeRepositoryImpl.java | io.releasehub.infrastructure.persistence |
| releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/security/BCryptPasswordService.java | io.releasehub.infrastructure.security |
| releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/security/JwtAuthenticationFilter.java | io.releasehub.infrastructure.security |
| releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/security/JwtService.java | io.releasehub.infrastructure.security |
| releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/security/SecurityConfig.java | io.releasehub.infrastructure.security |
| releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/security/UserDetailsServiceImpl.java | io.releasehub.infrastructure.security |
| releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/seed/DataSeeder.java | io.releasehub.infrastructure.seed |
| releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/user/UserJpaEntity.java | io.releasehub.infrastructure.user |
| releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/user/UserJpaRepository.java | io.releasehub.infrastructure.user |
| releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/user/UserRepositoryImpl.java | io.releasehub.infrastructure.user |

### Module: releasehub-interfaces
| Relative Path | Package Declaration |
|---|---|
| releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/ConfigureReleaseWindowRequest.java | io.releasehub.interfaces.api.releasewindow |
| releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/CreateReleaseWindowRequest.java | io.releasehub.interfaces.api.releasewindow |
| releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/ReleaseWindowController.java | io.releasehub.interfaces.api.releasewindow |
| releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/ReleaseWindowView.java | io.releasehub.interfaces.api.releasewindow |
| releasehub-interfaces/src/main/java/io/releasehub/interfaces/auth/AuthController.java | io.releasehub.interfaces.auth |
| releasehub-interfaces/src/main/java/io/releasehub/interfaces/rest/GlobalExceptionHandler.java | io.releasehub.interfaces.rest |
| releasehub-interfaces/src/main/java/io/releasehub/interfaces/rest/PingController.java | io.releasehub.interfaces.rest |

### Module: releasehub-bootstrap
| Relative Path | Package Declaration |
|---|---|
| releasehub-bootstrap/src/main/java/io/releasehub/bootstrap/config/OpenApiConfig.java | io.releasehub.bootstrap.config |
| releasehub-bootstrap/src/main/java/io/releasehub/bootstrap/ReleaseHubApplication.java | io.releasehub.bootstrap |
| releasehub-bootstrap/src/test/java/io/releasehub/arch/ArchitectureRulesTest.java | io.releasehub.arch |
| releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/api/AuthApiTest.java | io.releasehub.bootstrap.api |

### Module: releasehub-common
| Relative Path | Package Declaration |
|---|---|
| releasehub-common/src/main/java/io/releasehub/common/exception/BizException.java | io.releasehub.common.exception |
| releasehub-common/src/main/java/io/releasehub/common/response/ApiResponse.java | io.releasehub.common.response |

## 3. Layer Consistency Violations
- **releasehub-bootstrap**: `releasehub-bootstrap/src/test/java/io/releasehub/arch/ArchitectureRulesTest.java` (Package: `io.releasehub.arch`, Expected starts with: `io.releasehub.bootstrap`)

## 4. Key Entry Points & Gatekeepers
### ReleaseHubApplication
Path: `releasehub-bootstrap/src/main/java/io/releasehub/bootstrap/ReleaseHubApplication.java`
```java
package io.releasehub.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.releasehub")
public class ReleaseHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReleaseHubApplication.class, args);
    }
```

### ArchitectureRulesTest
Path: `./releasehub-bootstrap/src/test/java/io/releasehub/arch/ArchitectureRulesTest.java`
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

    @Test
    void domainLayerShouldBePure() {
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses().that().resideInAPackage("io.releasehub.domain..")
                .should().dependOnClassesThat(
                        com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("org.springframework..")
                        .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("org.hibernate.."))
                        .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("jakarta.persistence.."))
                        .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("javax.persistence.."))
                )
                .as("Domain layer must not depend on Spring, Hibernate, or Persistence frameworks")
                .check(classes);
    }

    @Test
    void commonLayerShouldBeRestricted() {
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses().that().resideInAPackage("io.releasehub.common..")
                .should().dependOnClassesThat(
                        com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("org.springframework.boot..")
                        .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("org.springframework.web.."))
                        .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("jakarta.persistence.."))
                        .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("org.hibernate.."))
                        .or(com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("org.springframework.data.."))
                )
                .as("Common layer must not depend on Spring Boot, Web, or Persistence frameworks")
                .check(classes);
    }
}
```

### Enforcer Configuration
#### ./releasehub-bootstrap/pom.xml
```xml
                <artifactId>maven-enforcer-plugin</artifactId>
                <executions>
                    <execution>
                        <id>enforce-banned-dependencies</id>
                        <phase>validate</phase>
                        <goals>
                            <goal>enforce</goal>
                        </goals>
                        <configuration>
                            <rules>
                                <bannedDependencies>
                                    <searchTransitive>false</searchTransitive>
                                    <excludes>
                                        <exclude>io.releasehub:releasehub-application</exclude>
                                        <exclude>io.releasehub:releasehub-domain</exclude>
                                        <exclude>io.releasehub:releasehub-common</exclude>
                                    </excludes>
                                    <message>Bootstrap layer must not directly depend on Application, Domain, or Common layers (use Interfaces/Infrastructure).</message>
                                </bannedDependencies>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot.version}</version>
                <executions>
```
#### ./releasehub-infrastructure/pom.xml
```xml
                <artifactId>maven-enforcer-plugin</artifactId>
                <executions>
                    <execution>
                        <id>enforce-banned-dependencies</id>
                        <phase>validate</phase>
                        <goals>
                            <goal>enforce</goal>
                        </goals>
                        <configuration>
                            <rules>
                                <bannedDependencies>
                                    <searchTransitive>false</searchTransitive>
                                    <excludes>
                                        <exclude>io.releasehub:releasehub-interfaces</exclude>
                                    </excludes>
                                    <message>Infrastructure layer must not directly depend on Interfaces layer.</message>
                                </bannedDependencies>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <skip>true</skip>
```
#### ./pom.xml
```xml
<!-- Enforcer plugin configured but no explicit bannedDependencies block found in simple grep -->
    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-enforcer-plugin</artifactId>
                    <version>3.5.0</version>
                    <executions>
                        <execution>
                            <id>enforce-rules</id>
                            <goals>
```
#### ./releasehub-domain/pom.xml
```xml
                <artifactId>maven-enforcer-plugin</artifactId>
                <executions>
                    <execution>
                        <id>enforce-banned-dependencies</id>
                        <phase>validate</phase>
                        <goals>
                            <goal>enforce</goal>
                        </goals>
                        <configuration>
                            <rules>
                                <bannedDependencies>
                                    <searchTransitive>false</searchTransitive>
                                    <excludes>
                                        <exclude>org.springframework:*</exclude>
                                        <exclude>org.springframework.boot:*</exclude>
                                        <exclude>org.springframework.data:*</exclude>
                                        <exclude>jakarta.persistence:*</exclude>
                                        <exclude>javax.persistence:*</exclude>
                                        <exclude>org.hibernate:*</exclude>
                                    </excludes>
                                    <message>Domain layer must remain pure and not depend on Spring, JPA, or Hibernate.</message>
                                </bannedDependencies>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```
#### ./releasehub-application/pom.xml
```xml
                <artifactId>maven-enforcer-plugin</artifactId>
                <executions>
                    <execution>
                        <id>enforce-banned-dependencies</id>
                        <phase>validate</phase>
                        <goals>
                            <goal>enforce</goal>
                        </goals>
                        <configuration>
                            <rules>
                                <bannedDependencies>
                                    <searchTransitive>false</searchTransitive>
                                    <excludes>
                                        <exclude>io.releasehub:releasehub-interfaces</exclude>
                                        <exclude>io.releasehub:releasehub-infrastructure</exclude>
                                        <exclude>io.releasehub:releasehub-bootstrap</exclude>
                                    </excludes>
                                    <message>Application layer must not directly depend on Interfaces, Infrastructure, or Bootstrap layers.</message>
                                </bannedDependencies>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```
#### ./releasehub-interfaces/pom.xml
```xml
                <artifactId>maven-enforcer-plugin</artifactId>
                <executions>
                    <execution>
                        <id>enforce-banned-dependencies</id>
                        <phase>validate</phase>
                        <goals>
                            <goal>enforce</goal>
                        </goals>
                        <configuration>
                            <rules>
                                <bannedDependencies>
                                    <searchTransitive>false</searchTransitive>
                                    <excludes>
                                        <exclude>io.releasehub:releasehub-infrastructure</exclude>
                                    </excludes>
                                    <message>Interfaces layer must not directly depend on Infrastructure layer.</message>
                                </bannedDependencies>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```
