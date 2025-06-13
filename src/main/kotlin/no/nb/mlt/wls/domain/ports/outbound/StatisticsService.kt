package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.events.Event

fun interface StatisticsService {
    suspend fun recordStatisticsEvent(event: Event)
}
