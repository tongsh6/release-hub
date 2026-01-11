package io.releasehub.bootstrap.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import jakarta.persistence.Entity;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.junit.jupiter.api.Assertions.fail;

public class ArchitectureRulesTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.releasehub");

    @Test
    @DisplayName("规则 A/B/C/D：各层依赖方向门禁")
    void enforceLayeredArchitecture() {
        layeredArchitecture()
                .consideringOnlyDependenciesInAnyPackage("io.releasehub..")
                .layer("Common").definedBy("io.releasehub.common..")
                .layer("Domain").definedBy("io.releasehub.domain..")
                .layer("Application").definedBy("io.releasehub.application..")
                .layer("Interfaces").definedBy("io.releasehub.interfaces..")
                .layer("Infrastructure").definedBy("io.releasehub.infrastructure..")
                .layer("Bootstrap").definedBy("io.releasehub.bootstrap..")

                // 规则 A：domain 包不得依赖 application / interfaces / infrastructure / bootstrap
                .whereLayer("Domain").mayOnlyAccessLayers("Domain", "Common")

                // 规则 B：application 包不得依赖 infrastructure / interfaces / bootstrap
                .whereLayer("Application").mayOnlyAccessLayers("Domain", "Common")

                // 规则 C：interfaces 包不得直接依赖 infrastructure.persistence 或 infrastructure.security
                // (只能依赖 application 的 Port 与 AppService)
                // We enforce stricter: Interfaces can only access Application, Domain, Common.
                .whereLayer("Interfaces").mayOnlyAccessLayers("Application", "Domain", "Common")

                // 规则 D：infrastructure 允许依赖 application（实现 Port），但不得被 application 反向依赖
                // (Application restriction is covered by Rule B)
                .whereLayer("Infrastructure").mayOnlyAccessLayers("Application", "Domain", "Common")

                // Bootstrap can access everything
                .whereLayer("Bootstrap").mayOnlyAccessLayers("Interfaces", "Infrastructure", "Application", "Domain", "Common")

                .check(classes);
    }

    @Test
    @DisplayName("规则 E：Bootstrap 层不得包含业务逻辑组件")
    void bootstrapShouldNotContainBusinessLogic() {
        noClasses().that().resideInAPackage("io.releasehub.bootstrap..")
                .should().beAnnotatedWith(Entity.class)
                .orShould().beAnnotatedWith(Repository.class)
                .orShould().beAnnotatedWith(Service.class)
                .as("Bootstrap 不得包含任何 @Entity / @Repository / @Service（只允许装配类 @Configuration、启动类）")
                .check(classes);
    }

    @Test
    @DisplayName("规则 3：Port/Adapter 命名加约束（防止回退）")
    void enforceNamingConventions() {
        // application 层对外抽象必须以 Port 或 Gateway 结尾
        // Enforce for all interfaces in application layer.
        classes().that().resideInAPackage("..application..")
                .and().areInterfaces()
                .should().haveSimpleNameEndingWith("Port")
                .orShould().haveSimpleNameEndingWith("Gateway")
                .as("Application 层接口（Port）必须以 Port 或 Gateway 结尾")
                .check(classes);

        // infrastructure 实现必须以 Adapter 或 PersistenceAdapter 结尾
        classes().that().resideInAPackage("..infrastructure..")
                .and().implement(
                        com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("..application..")
                )
                .should().haveSimpleNameEndingWith("Adapter")
                .orShould().haveSimpleNameEndingWith("PersistenceAdapter")
                .as("Infrastructure 层实现 Application 接口的类必须以 Adapter 或 PersistenceAdapter 结尾")
                .check(classes);
    }

    @Test
    @DisplayName("规则 2：资源结构门禁（Migration 位置检查）")
    void enforceResourceStructure() throws IOException {
        String projectRoot = System.getProperty("user.dir");
        // Adjust if running from module root vs project root
        // If running "mvn test" from root, user.dir is root.
        // We need to find the root.
        File rootDir = new File(projectRoot);
        if (new File(rootDir, "releasehub-bootstrap").exists()) {
            // We are likely in root
        } else if (rootDir.getName().equals("releasehub-bootstrap")) {
            rootDir = rootDir.getParentFile();
        } else {
            // Fallback: traverse up until we find .git or pom.xml with modules
             while (rootDir != null && !new File(rootDir, "releasehub-bootstrap").exists()) {
                 rootDir = rootDir.getParentFile();
             }
        }
        
        if (rootDir == null) {
            // Cannot find root, skip or fail? Fails safe.
            fail("Cannot determine project root directory to check resource structure.");
        }

        // 1. 断言仓库中不存在 releasehub-bootstrap/**/db/migration
        Path bootstrapMigration = Paths.get(rootDir.getAbsolutePath(), "releasehub-bootstrap", "src", "main", "resources", "db", "migration");
        if (Files.exists(bootstrapMigration)) {
             try (Stream<Path> files = Files.walk(bootstrapMigration)) {
                 if (files.filter(Files::isRegularFile).findAny().isPresent()) {
                     fail("违反规则：releasehub-bootstrap 下发现 db/migration 目录或文件。请将 SQL 迁移脚本移动到 releasehub-infrastructure。");
                 }
             }
        }

        // 2. 断言 db/migration 只存在于 releasehub-infrastructure
        // Check if other modules have it (optional, but good)
        String[] modules = {"releasehub-application", "releasehub-domain", "releasehub-interfaces", "releasehub-common"};
        for (String module : modules) {
            Path moduleMigration = Paths.get(rootDir.getAbsolutePath(), module, "src", "main", "resources", "db", "migration");
             if (Files.exists(moduleMigration)) {
                 try (Stream<Path> files = Files.walk(moduleMigration)) {
                     if (files.filter(Files::isRegularFile).findAny().isPresent()) {
                         fail("违反规则：" + module + " 下发现 db/migration 目录或文件。SQL 迁移脚本只能存在于 releasehub-infrastructure。");
                     }
                 }
            }
        }
        
        // Ensure infrastructure HAS it (optional, but ensures we don't lose them)
        Path infraMigration = Paths.get(rootDir.getAbsolutePath(), "releasehub-infrastructure", "src", "main", "resources", "db", "migration");
        if (!Files.exists(infraMigration)) {
             // It's okay if it doesn't exist yet? User said "Assert db/migration ONLY exists in infrastructure".
             // But user also said "infrastructure 允许存在".
             // Let's pass if it's missing, but fail if it's elsewhere.
        }
    }
}
