package no.nb.mlt.wls

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing
import org.springframework.scheduling.annotation.EnableScheduling
import reactor.core.publisher.Hooks

@ConfigurationPropertiesScan
@SpringBootApplication
@EnableReactiveMongoAuditing
@EnableScheduling
class HermesApplication

fun main(args: Array<String>) {
    runApplication<HermesApplication>(*args)
}
