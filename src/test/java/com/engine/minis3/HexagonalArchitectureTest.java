package com.engine.minis3;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class HexagonalArchitectureTest {

    @Test
    void testDomainHasZeroFrameworkDependencies() {
        JavaClasses importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.engine.minis3");

        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "com.fasterxml.jackson..",
                        "..infrastructure..",
                        "..application.."
                )
                .because("Domain layer must be strictly pure with zero framework or outer layer dependencies.")
                .check(importedClasses);
    }
}
