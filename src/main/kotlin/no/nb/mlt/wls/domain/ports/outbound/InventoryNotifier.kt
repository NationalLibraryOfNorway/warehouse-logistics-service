package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import java.time.Instant

/**
 * Provides notification mechanisms for inventory-related events.
 * This interface defines methods to notify changes in inventory items and orders.
 */
interface InventoryNotifier {
    @Throws(UnableToNotifyException::class)
    fun itemChanged(
        item: Item,
        eventTimestamp: Instant,
        messageId: String
    )

    @Throws(UnableToNotifyException::class)
    fun orderChanged(
        order: Order,
        eventTimestamp: Instant,
        messageId: String
    )
}

class UnableToNotifyException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
