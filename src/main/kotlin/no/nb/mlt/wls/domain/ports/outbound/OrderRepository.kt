package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.OrderNotFoundException

interface OrderRepository {
    suspend fun getOrder(
        hostName: HostName,
        hostOrderId: String
    ): Order?

    suspend fun getAllOrdersForHosts(hostnames: List<HostName>): List<Order>

    @Throws(OrderNotFoundException::class)
    suspend fun deleteOrder(order: Order)

    suspend fun updateOrder(order: Order): Order

    suspend fun createOrder(order: Order): Order

    suspend fun getOrdersWithItems(
        hostName: HostName,
        orderItemIds: List<String>
    ): List<Order>
}

class OrderUpdateException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
