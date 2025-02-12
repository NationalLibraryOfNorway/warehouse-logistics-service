package no.nb.mlt.wls

import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName

class TestcontainerInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    companion object {
        const val MAILHOG_SMTP_PORT = 1025
        const val MAILHOG_HTTP_PORT = 8025
        val MongoContainer = MongoDBContainer(DockerImageName.parse("mongo:4.4.22"))
        val DummySynqContainer = GenericContainer(DockerImageName.parse("harbor.nb.no/mlt/dummy-synq:main"))
        val MailhogContainer =
            GenericContainer(DockerImageName.parse("mailhog/mailhog"))
                .withExposedPorts(MAILHOG_SMTP_PORT, MAILHOG_HTTP_PORT)

        init {
            Startables.deepStart(MongoContainer, MailhogContainer, DummySynqContainer).join()
        }
    }

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        TestPropertyValues.of("spring.data.mongodb.uri=" + MongoContainer.replicaSetUrl).applyTo(applicationContext)
        TestPropertyValues.of("spring.mail.host=" + MailhogContainer.host).applyTo(applicationContext)
        TestPropertyValues.of("spring.mail.port=" + MailhogContainer.getMappedPort(MAILHOG_SMTP_PORT)).applyTo(applicationContext)
    }
}
