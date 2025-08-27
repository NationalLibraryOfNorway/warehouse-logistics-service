package no.nb.mlt.wls

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import org.junit.jupiter.api.TestInstance
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AnalyzeClasses(packages = ["no.nb.mlt.wls"])
internal class ArchitectureTest {
    @ArchTest
    fun `The domain model does not have any outgoing dependencies`(javaClasses: JavaClasses) {
        noClasses()
            .that()
            .resideInAPackage("no.nb.mlt.wls.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("no.nb.mlt.wls.infrastructure..", "no.nb.mlt.wls.application..")
            .check(javaClasses)
    }

    @ArchTest
    fun `The application layer does not access any adapters`(javaClasses: JavaClasses) {
        noClasses()
            .that()
            .resideInAPackage("no.nb.mlt.wls.application..")
            .should()
            .accessClassesThat()
            .resideInAnyPackage("no.nb.mlt.wls.infrastructure..")
            .check(javaClasses)
    }

    @ArchTest
    fun `The infrastructure packages does not access any application classes`(javaClasses: JavaClasses) {
        noClasses()
            .that()
            .resideInAPackage("no.nb.mlt.wls.infrastructure..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("no.nb.mlt.wls.application..")
            .check(javaClasses)
    }

    @ArchTest
    fun `An adapter should not access another adapter`(javaClasses: JavaClasses) {
        slices()
            .matching("no.nb.mlt.wls.infrastructure..(*)")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(
                nameMatching("no.nb.mlt.wls.infrastructure.config.*"),
                DescribedPredicate.alwaysTrue()
            ).ignoreDependency(
                DescribedPredicate.alwaysTrue(),
                nameMatching("no.nb.mlt.wls.infrastructure.config.*")
            ).check(javaClasses)
    }

    @ArchTest
    fun `Domain classes should not be directly used as controller ResponseEntity return types`(javaClasses: JavaClasses) {
        javaClasses.forEach { clazz ->
            if (clazz.packageName.contains("no.nb.mlt.wls.application")) {
                clazz.methods.forEach { method ->
                    if (method.isAnnotatedWith(GetMapping::class.java)) {
                        assert(doesMethodReturnDomainClass(method, clazz))
                    }
                    if (method.isAnnotatedWith(PostMapping::class.java)) {
                        assert(doesMethodReturnDomainClass(method, clazz))
                    }
                    if (method.isAnnotatedWith(PutMapping::class.java)) {
                        assert(doesMethodReturnDomainClass(method, clazz))
                    }
                    if (method.isAnnotatedWith(DeleteMapping::class.java)) {
                        assert(doesMethodReturnDomainClass(method, clazz))
                    }
                }
            }
        }
    }

    /**
     * Checks the last parameter of a method and whether it resides in the domain package.
     * This is usually the return type.
     */
    private fun doesMethodReturnDomainClass(
        method: JavaMethod,
        clazz: JavaClass
    ): Boolean {
        if (method.parameters
                .last()
                .type.allInvolvedRawTypes
                .last()
                .packageName
                .contains("no.nb.mlt.wls.domain") == true
        ) {
            val domainClassName =
                method.parameters
                    .last()
                    .type.allInvolvedRawTypes
                    .last()
                    .name
            println("Method ${method.name} in ${clazz.name} contains domain class: $domainClassName")
            return false
        }
        return true
    }
}
