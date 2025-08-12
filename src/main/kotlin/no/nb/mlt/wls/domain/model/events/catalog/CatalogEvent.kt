package no.nb.mlt.wls.domain.model.events.catalog

import no.nb.mlt.wls.domain.model.events.Event
import java.time.Instant

/**
 * Represents a catalog-related event in the system. Catalog events are domain events pertaining
 * to item or order updates, which are part of the catalog's life cycle. For example, when an
 * order is picked, an order event will be fired off to update the catalog about it.
 *
 * This sealed interface ensures that all implementations are known at compile-time, allowing for
 * exhaustive handling of all event types by the processor.
 *
 * Every [CatalogEvent] has the following properties:
 * - A unique identifier inherited from the [Event] interface.
 * - A body representing the domain entity related to the event (e.g., an item or an order).
 * - A timestamp denoting when the event occurred.
 *
 * Each event might also include event-type-specific info.
 */
sealed interface CatalogEvent : Event {
    val eventTimestamp: Instant
}
