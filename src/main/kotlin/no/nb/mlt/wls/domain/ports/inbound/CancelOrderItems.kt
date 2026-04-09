package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order.OrderItem

/**
 * Port for cancelling order items from being picked.
 *
 * This interface is designed to handle cases where items in order lines should be marked as [OrderItem.Status.FAILED]
 * This happens in some scenarios if a storage operator manually cancels an order
 * in a storage system.
 */
fun interface CancelOrderItems {
    suspend fun cancelOrderItems(hostName: HostName, hostOrderId: String, cancelledItemIds: List<String>)
}
