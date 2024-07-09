package no.nb.mlt.wls

import org.springframework.test.context.ContextConfiguration

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ContextConfiguration(initializers = [TestcontainerInitializer::class])
annotation class EnableTestcontainers
