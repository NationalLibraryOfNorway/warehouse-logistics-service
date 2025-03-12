package no.nb.mlt.wls

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AnalyzeClasses(packages = ["no.nb.mlt.wls"])
internal class ArchitectureTest {
    @ArchTest
    fun `The domain model does not have any outgoing dependencies`(javaClasses: JavaClasses) {
        noClasses()
            .that()
            .resideInAPackage("no.nb.mlt.wls.domain..")
            .should()
            .accessClassesThat()
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
            .accessClassesThat()
            .resideInAnyPackage("no.nb.mlt.wls.application..")
            .check(javaClasses)
    }

    @ArchTest
    fun `An adapter should not access another adapter`(javaClasses: JavaClasses) {
        slices()
            .matching("no.nb.mlt.wls.infrastructure..(*)")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(nameMatching(".*Config"), DescribedPredicate.alwaysTrue())
            .check(javaClasses)
    }
}
