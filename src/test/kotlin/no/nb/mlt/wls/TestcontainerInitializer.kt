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
        val MongoContainer = MongoDBContainer(DockerImageName.parse("mongo:4.4.22"))
        val DummySynqContainer = GenericContainer(DockerImageName.parse("harbor.nb.no/mlt/dummy-synq:main"))

        init {
            Startables.deepStart(MongoContainer, DummySynqContainer).join()
        }
    }

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        TestPropertyValues.of("spring.data.mongodb.uri=" + MongoContainer.replicaSetUrl).applyTo(applicationContext)
        // Temporary solution while we are working on implementing a test container for SynQ
        TestPropertyValues.of("synq.path.base=http://localhost:8181/synq/resources").applyTo(applicationContext)
    }
}
