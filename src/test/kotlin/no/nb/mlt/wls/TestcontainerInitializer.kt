package no.nb.mlt.wls

import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName

class TestcontainerInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    companion object {
        val MongoContainer = MongoDBContainer(DockerImageName.parse("mongo:4.4.22"))

        init {
            Startables.deepStart(MongoContainer).join()
        }
    }

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        TestPropertyValues.of("spring.data.mongodb.uri=" + MongoContainer.replicaSetUrl).applyTo(applicationContext)
        // TODO - Make a mock endpoint for SynQ
        TestPropertyValues.of("synq.path.base=").applyTo(applicationContext)
        TestPropertyValues.of("synq.path.createProduct=").applyTo(applicationContext)
    }
}
