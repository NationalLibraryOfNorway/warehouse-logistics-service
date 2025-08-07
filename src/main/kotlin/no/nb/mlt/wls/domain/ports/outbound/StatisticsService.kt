package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.events.Event

/**
 * Defines an interface responsible for recording statistics events.
 *
 * Any implementation of this service interface is tasked with processing and managing
 * statistical data encapsulated in the form of [Event]s.
 *
 * @see Event
 */
fun interface StatisticsService {
    suspend fun recordStatisticsEvent(event: Event)
}
