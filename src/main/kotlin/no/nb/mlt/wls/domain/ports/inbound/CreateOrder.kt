package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order

fun interface CreateOrder {
    suspend fun createOrder(orderDTO: CreateOrderDTO): Order
}

data class CreateOrderDTO(
    val hostName: HostName,
    val hostOrderId: String,
    val orderLine: List<OrderItem>,
    val orderType: Order.Type,
    val contactPerson: String,
    val contactEmail: String?,
    val address: Order.Address?,
    val note: String?,
    val callbackUrl: String
) {
    data class OrderItem(
        val hostId: String
    )
}

fun CreateOrderDTO.toOrder(): Order =
    Order(
        hostName = hostName,
        hostOrderId = hostOrderId,
        status = Order.Status.NOT_STARTED,
        orderLine =
            orderLine.map {
                Order.OrderItem(it.hostId, Order.OrderItem.Status.NOT_STARTED)
            },
        orderType = orderType,
        contactPerson = contactPerson,
        contactEmail = contactEmail,
        address = address,
        note = note,
        callbackUrl = callbackUrl
    )
