package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import kotlin.jvm.Throws

interface UpdateOrder {
    @Throws(OrderNotFoundException::class)
    suspend fun updateOrder(
        hostName: HostName,
        hostOrderId: String,
        orderItems: List<Order.OrderItem>,
        orderType: Order.Type,
        receiver: Order.Receiver,
        callbackUrl: String
    ): Order
}
