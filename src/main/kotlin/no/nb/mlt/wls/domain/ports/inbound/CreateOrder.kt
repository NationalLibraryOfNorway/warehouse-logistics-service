package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Owner

interface CreateOrder {
    suspend fun createOrder(orderDTO: CreateOrderDTO): Order
}

data class CreateOrderDTO(
    val hostName: HostName,
    val hostOrderId: String,
    val orderItems: List<OrderItem>,
    val orderType: Order.Type,
    val owner: Owner?,
    val receiver: Order.Receiver,
    val callbackUrl: String
) {
    data class OrderItem(
        val hostId: String
    )
}

fun CreateOrderDTO.toOrder(): Order {
    return Order(
        hostName = hostName,
        hostOrderId = hostOrderId,
        status = Order.Status.NOT_STARTED,
        productLine =
            orderItems.map {
                Order.OrderItem(it.hostId, Order.OrderItem.Status.NOT_STARTED)
            },
        orderType = orderType,
        owner = owner,
        receiver = receiver,
        callbackUrl = callbackUrl
    )
}
