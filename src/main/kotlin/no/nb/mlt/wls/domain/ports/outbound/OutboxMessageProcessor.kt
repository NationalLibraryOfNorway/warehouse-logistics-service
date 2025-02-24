package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.outboxMessages.OutboxMessage

interface OutboxMessageProcessor {
    suspend fun handleEvent(event: OutboxMessage)
}
