package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.outboxMessages.OutboxMessage

interface OutboxRepository {
    suspend fun save(outboxMessage: OutboxMessage): OutboxMessage

    suspend fun getAll(): List<OutboxMessage>

    suspend fun getUnprocessedSortedByCreatedTime(): List<OutboxMessage>

    suspend fun markAsProcessed(outboxMessage: OutboxMessage): OutboxMessage
}
