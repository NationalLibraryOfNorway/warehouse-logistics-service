package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.events.Event

fun interface EventProcessor<in T : Event> {
    suspend fun handleEvent(event: T)
}
