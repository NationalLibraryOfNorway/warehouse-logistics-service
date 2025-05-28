package no.nb.mlt.wls.domain.model.statistics

import java.time.Instant

data class ItemStatisticsEvent(
    val itemId: String,
    override val eventType: String,
    override val details: Map<String, Any>,
    override val timestamp: Instant = Instant.now()
) : StatisticsEvent {
    override val id: String
        get() = itemId
}
