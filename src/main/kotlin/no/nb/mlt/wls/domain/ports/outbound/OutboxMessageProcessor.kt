package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.outboxMessages.OutboxMessage

fun interface OutboxMessageProcessor {
    suspend fun handleEvent(event: OutboxMessage)
}
