package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order

/**
 * Represents an email notification system for managing events related to orders.
 */
fun interface EmailNotifier {
    suspend fun orderCreated(
        order: Order,
        orderItems: List<Item>
    )
}
