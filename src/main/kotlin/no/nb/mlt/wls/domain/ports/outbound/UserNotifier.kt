package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order

/**
 * Represents a notification system for managing events related to orders and items.
 * Booleans on the methods return true if the email was sent without any exceptions or errors.
 * Returns false otherwise.
 */
interface UserNotifier {
    suspend fun orderConfirmation(order: Order): Boolean

    suspend fun orderPickup(
        order: Order,
        items: List<Item>
    ): Boolean

    suspend fun orderCompleted(order: Order): Boolean
}
