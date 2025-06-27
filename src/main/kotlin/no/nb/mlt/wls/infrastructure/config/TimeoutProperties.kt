package no.nb.mlt.wls.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("timeout")
class TimeoutProperties(
    private val mongoTimeout: Int,
    private val inventoryTimeout: Int,
    private val storageTimeout: Int
) {
    val mongo: Duration get() = Duration.ofSeconds(mongoTimeout.toLong())

    val inventory: Duration get() = Duration.ofSeconds(inventoryTimeout.toLong())

    val storage: Duration get() = Duration.ofSeconds(storageTimeout.toLong())
}
