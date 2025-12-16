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
