package no.nb.mlt.wls.domain.model.statistics

import no.nb.mlt.wls.domain.model.events.catalog.CatalogEvent
import no.nb.mlt.wls.domain.model.events.storage.ItemCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderDeleted
import no.nb.mlt.wls.domain.model.events.storage.StorageEvent
import java.time.Instant

/**
 * Represents a statistical event related to an item or order.
 *
 * Implementers of this interface represent different types of statistical events
 * such as item-related or order-related events. These events are characterized
 * by unique identifiers, timestamps, event types, and metadata details.
 *
 * Properties:
 * - `id`: A unique identifier for the event, either item or order id.
 * - `timestamp`: The time at which the event occurred.
 * - `eventType`: The type of event being represented (e.g., [ItemCreated] or [OrderDeleted]).
 * - `details`: A map containing additional metadata about the event.
 */
sealed interface StatisticsEvent {
    /**
     * A unique identifier for the statistical event.
     */
    val id: String

    /**
     * Timestamp at which this event occurred.
     */
    val timestamp: Instant

    /**
     * Specifies the type of the statistical event, such as "ItemCreated", "OrderDeleted", etc.
     * Corresponds to names of the Storage and Catalog events.
     * @see CatalogEvent
     * @see StorageEvent
     */
    val eventType: String

    /**
     * Details specific to the event type that's being recorded.
     */
    val details: Map<String, Any>
}
