package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order

/**
 * A port for retrieving an order based on a given host name and order ID.
 *
 * The function returns the corresponding [Order] if found, or `null` if we cannot retrieve the order.
 *
 * @see Order
 */
fun interface GetOrder {
    suspend fun getOrder(
        hostName: HostName,
        hostOrderId: String
    ): Order?
}
