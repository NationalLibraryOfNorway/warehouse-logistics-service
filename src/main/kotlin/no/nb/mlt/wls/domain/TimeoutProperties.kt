package no.nb.mlt.wls.domain

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.Duration.ofSeconds as fromSeconds

@ConfigurationProperties("timeout")
class TimeoutProperties(
    private val mongoTimeout: Int,
    private val inventoryTimeout: Int,
    private val storageTimeout: Int
) {
    val mongo: Duration get() = fromSeconds(mongoTimeout.toLong())

    val inventory: Duration get() = fromSeconds(inventoryTimeout.toLong())

    val storage: Duration get() = fromSeconds(storageTimeout.toLong())
}
