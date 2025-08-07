package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.events.Event

/**
 * Represents a repository interface for managing events of a specific type.
 *
 * This interface provides methods for persisting, retrieving, and managing events,
 * particularly focusing on unprocessed events. It facilitates the handling of domain-specific
 * events by supporting operations like saving events, fetching all events, retrieving specific
 * subsets of events based on processing status, and marking events as processed.
 *
 * @param T the type of events this repository is designed to handle. T must be a subtype of [Event].
 * @see Event
 */
interface EventRepository<T : Event> {
    suspend fun save(event: T): T

    suspend fun getAll(): List<T>

    suspend fun getUnprocessedSortedByCreatedTime(): List<T>

    suspend fun markAsProcessed(event: T): T
}
