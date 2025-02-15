package no.nb.mlt.wls.domain.ports.outbound

interface OutboxRepository {
    suspend fun save(outboxMessage: OutboxMessage): OutboxMessage

    suspend fun getAll(): List<OutboxMessage>
}

interface OutboxMessage {
    val body: Any
}
