package no.nb.mlt.wls.domain.model.statistics

import java.time.Instant

data class OrderStatisticsEvent(
    val orderId: String,
    override val eventType: String,
    override val details: Map<String, Any>,
    override val timestamp: Instant = Instant.now()
) : StatisticsEvent {
    override val id: String
        get() = orderId
}
