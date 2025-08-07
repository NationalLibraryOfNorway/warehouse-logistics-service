package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order

/**
 * A port for handling the process of marking specific items in an order as picked.
 *
 * This interface is used to execute the logic associated with picking specific items from
 * a given order.
 *
 * The purpose of this operation is to update the status of items within an order,
 * adjust inventory quantities, update order status, notify catalogs, etc.
 *
 * @see Item
 * @see Order
 */
fun interface PickOrderItems {
    suspend fun pickOrderItems(
        hostName: HostName,
        pickedItemIds: List<String>,
        orderId: String
    )
}
