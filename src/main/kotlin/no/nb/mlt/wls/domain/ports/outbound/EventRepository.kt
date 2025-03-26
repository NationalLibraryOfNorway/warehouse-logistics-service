package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.Event

interface EventRepository<T : Event> {
    suspend fun save(event: T): T

    suspend fun getAll(): List<T>

    suspend fun getUnprocessedSortedByCreatedTime(): List<T>

    suspend fun markAsProcessed(event: T): T
}
