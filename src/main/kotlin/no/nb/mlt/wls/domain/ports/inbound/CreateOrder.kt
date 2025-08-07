package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order

/**
 * A port for creating new orders in the system.
 *
 * This port accepts metadata about the order and a list of items.
 * The items are expected to be valid IDs of existing items in the system.
 * It returns a fully created [Order] instance.
 *
 * This interface provides an abstraction for the underlying
 * mechanism of creating an order and is designed to be implemented
 * with a specific order creation logic.
 *
 * @see CreateOrderDTO
 * @see Order
 */
fun interface CreateOrder {
    suspend fun createOrder(orderDTO: CreateOrderDTO): Order
}

/**
 * This object encapsulates the necessary details required to create an order and map it to a domain-level data structure.
 *
 * @property hostName The host to which the order should be directed.
 * @property hostOrderId A unique identifier for the order as per the host system.
 * @property orderLine A list of items included in the order.
 * @property orderType The type of the order being created.
 * @property contactPerson The name of the person to be contacted regarding the order.
 * @property contactEmail The email address of the contact person, if available.
 * @property address The address associated with the order, if applicable.
 * @property note Additional notes or comments related to the order, if any.
 * @property callbackUrl The URL where updates about the order can be posted.
 * @see Order
 * @see OrderItem
 */
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
    /**
     * Represents an individual item in a new order, only holding its ID as that's the only necessary information.
     *
     * @property hostId A unique identifier for the item within the host system.
     */
    data class OrderItem(
        val hostId: String
    )

    fun toOrder(): Order =
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
}
