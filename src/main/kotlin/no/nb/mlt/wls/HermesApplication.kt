package no.nb.mlt.wls

import no.nb.mlt.wls.infrastructure.ProxyConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing
import org.springframework.scheduling.annotation.EnableScheduling

@ConfigurationPropertiesScan
@SpringBootApplication
@EnableReactiveMongoAuditing
@EnableScheduling
class HermesApplication

fun main(args: Array<String>) {
    val context: ApplicationContext = runApplication<HermesApplication>(*args)
    val proxyConfig = context.getBean(ProxyConfig::class.java)
}
