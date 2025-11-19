package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order

/**
 * Represents an email notification system for managing events related to orders.
 */
interface EmailNotifier {
    suspend fun sendOrderConfirmation(order: Order)

    suspend fun sendOrderHandlerMail(
        order: Order,
        items: List<Item>
    )

    suspend fun orderCompleted(order: Order)
}
