package no.nb.mlt.wls.domain.model.events.storage

import no.nb.mlt.wls.domain.model.events.Event

/**
 * Represents a storage-related event in the system. Storage events are domain events pertaining
 * to items or orders in storage systems. For example, when an item is created it will emit an
 * [ItemCreated] event.
 *
 * This sealed interface ensures that all implementations are known at compile-time, allowing for
 * exhaustive handling of all event types by the processor.
 *
 * Every [StorageEvent] has the following properties:
 * - A unique identifier inherited from the [Event] interface.
 * - A body representing the domain entity related to the event (e.g., an item or an order).
 * - A timestamp denoting when the event occurred.
 *
 * Each event might also include event-type-specific info.
 */
sealed interface StorageEvent : Event
