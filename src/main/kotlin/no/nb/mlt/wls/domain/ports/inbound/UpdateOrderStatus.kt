package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order

/**
 * Functional interface that defines a method to update the status of an [Order] in the system.
 *
 * This interface is designed to handle the transition of an order's status for a specific host.
 * The implementation will identify the order to be updated by its unique host name and order ID,
 * and then update its status to the given value.
 *
 * This operation may throw an [OrderNotFoundException] if the order with the specified host order ID cannot be found.
 * It may also throw and [IllegalOrderStateException] if the new status is not valid for the current status.
 *
 * @see Order
 * @see OrderNotFoundException
 */
fun interface UpdateOrderStatus {
    @Throws(OrderNotFoundException::class)
    suspend fun updateOrderStatus(
        hostName: HostName,
        hostOrderId: String,
        status: Order.Status
    ): Order
}
