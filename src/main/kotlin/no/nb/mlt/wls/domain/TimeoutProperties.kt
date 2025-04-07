package no.nb.mlt.wls.domain

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("timeout")
class TimeoutProperties(
    private val mongo: Int,
    private val inventory: Int,
    private val storage: Int
) {
    fun mongoTimeout(): Duration {
        return Duration.ofSeconds(mongo.toLong())
    }

    fun inventory(): Duration {
        return Duration.ofSeconds(inventory.toLong())
    }

    fun storage(): Duration {
        return Duration.ofSeconds(storage.toLong())
    }
}
