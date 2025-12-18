package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.events.email.OrderPickupMail

/**
 * Represents a notification system for managing events related to orders and items.
 * Booleans on the methods return true if the email was sent without any exceptions or errors.
 * Returns false otherwise.
 */
interface UserNotifier {
    suspend fun orderConfirmation(order: Order): Boolean

    suspend fun orderPickup(orderPickupData: OrderPickupMail.OrderPickupData): Boolean

    suspend fun orderCompleted(order: Order): Boolean

    suspend fun orderCancelled(order: Order): Boolean
}
