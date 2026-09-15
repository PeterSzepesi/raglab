package com.example.arxivrag;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularArchitectureTests {

    private static final String ROOT = "com.example.arxivrag.";

    private final com.tngtech.archunit.core.domain.JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example.arxivrag");

    @Test
    void conversationDoesNotDependOnTheFoundryAdapter() {
        noClasses().that().resideInAPackage("..ai.chat..")
                .and().haveSimpleNameNotEndingWith("CommandLineRunner")
                .should().dependOnClassesThat().resideInAPackage("..ai.foundry..")
                .check(productionClasses);
    }

    @Test
    void foundryAdaptersDoNotDependOnConversationOrWebTypes() {
        noClasses().that().resideInAPackage("..ai.foundry..")
                .should().dependOnClassesThat().resideInAnyPackage("..ai.chat..", "..web..")
                .check(productionClasses);
    }

    @Test
    void logicalApplicationModulesAreFreeOfCycles() {
        slices().assignedFrom(new LogicalModuleAssignment()).namingSlices("$1")
                .should().beFreeOfCycles().check(productionClasses);
    }

    @Test
    void providerApiModuleVerifiesWithSpringModulith() {
        var modules = ApplicationModules.of(ArxivRagApplication.class,
                JavaClass.Predicates.resideOutsideOfPackages(ROOT + "model.."));
        modules.verify();
        new org.springframework.modulith.docs.Documenter(modules).writeModulesAsPlantUml();
    }

    /** Maps legacy package locations to the feature modules documented in docs/architecture.md. */
    private static final class LogicalModuleAssignment implements SliceAssignment {
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass type) {
            var packageName = type.getPackageName();
            var simpleName = type.getSimpleName();
            if (!packageName.startsWith(ROOT) || packageName.startsWith(ROOT + "config")
                    || simpleName.endsWith("Controller") || simpleName.endsWith("CommandLineRunner")) {
                return SliceIdentifier.ignore();
            }
            if (packageName.startsWith(ROOT + "ai.chat")) return module("conversation");
            if (packageName.startsWith(ROOT + "ai.foundry") || packageName.startsWith(ROOT + "model")) {
                return module("model");
            }
            if (packageName.startsWith(ROOT + "arxiv") || packageName.startsWith(ROOT + "paper")) {
                return module("catalog");
            }
            if (packageName.startsWith(ROOT + "search")) {
                return "CorpusGeneration".equals(simpleName) ? module("catalog") : module("retrieval");
            }
            if (packageName.startsWith(ROOT + "vector")) return module("retrieval");
            if (packageName.startsWith(ROOT + "embedding")) {
                if (java.util.Set.of("EmbeddingModelGateway", "EmbeddingWorkClass").contains(simpleName)) {
                    return module("model");
                }
                if (java.util.Set.of("RetrievalDocument", "RetrievalDocumentFactory").contains(simpleName)) {
                    return module("catalog");
                }
                return module("indexing");
            }
            return SliceIdentifier.ignore();
        }

        @Override
        public String getDescription() {
            return "classes assigned to catalog, indexing, retrieval, conversation, model, and operations";
        }

        private static SliceIdentifier module(String name) {
            return SliceIdentifier.of(name);
        }
    }
}
