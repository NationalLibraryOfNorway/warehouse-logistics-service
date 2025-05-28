package no.nb.mlt.wls.domain.model.statistics

import java.time.Instant

sealed interface StatisticsEvent {
    val id: String
    val timestamp: Instant
    val eventType: String
    val details: Map<String, Any>
}
