package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order

/**
 * Represents an email notification system for managing events related to orders.
 * Booleans on the methods return true if the email was sent without any exceptions or errors.
 * Returns false otherwise.
 */
interface EmailNotifier {
    suspend fun sendOrderConfirmation(order: Order): Boolean

    suspend fun sendOrderHandlerMail(
        order: Order,
        items: List<Item>
    ): Boolean

    suspend fun orderCompleted(order: Order): Boolean
}
