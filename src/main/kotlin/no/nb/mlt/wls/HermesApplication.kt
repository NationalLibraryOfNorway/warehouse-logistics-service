package no.nb.mlt.wls

import no.nb.mlt.wls.infrastructure.ProxyConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import kotlin.jvm.java

@ConfigurationPropertiesScan
@SpringBootApplication
class HermesApplication

fun main(args: Array<String>) {
    val context: ApplicationContext = runApplication<HermesApplication>(*args)
    val proxyConfig = context.getBean(ProxyConfig::class.java)
}
