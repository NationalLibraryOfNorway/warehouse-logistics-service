package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.events.Event

/**
 * Defines an interface for processing domain-specific events.
 *
 * Any class implementing this interface is responsible for handling
 * events of type T and processing pending events stored in an outbox.
 *
 * @param T the type of events this processor is designed to handle. T must be a subtype of [Event].
 * @see Event
 */
interface EventProcessor<in T : Event> {
    suspend fun handleEvent(event: T)

    suspend fun processOutbox()
}
