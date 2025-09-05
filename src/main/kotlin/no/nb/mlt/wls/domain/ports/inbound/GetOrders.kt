package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order

/**
 * A port for retrieving all orders from specified host systems.
 * Given a list of [HostName], it retrieves the corresponding [Order] entities
 * associated with those hosts.
 *
 * This interface is typically used for scenarios where multiple host systems
 * need to be queried to retrieve their respective orders. The function is
 * asynchronous and returns a list of orders that belong to the specified hostnames.
 *
 * @see Order
 * @see HostName
 */
interface GetOrders {
    suspend fun getAllOrders(hostnames: List<HostName>): List<Order>

    suspend fun getOrdersById(
        hostNames: List<HostName>,
        hostOrderId: String
    ): List<Order>
}
