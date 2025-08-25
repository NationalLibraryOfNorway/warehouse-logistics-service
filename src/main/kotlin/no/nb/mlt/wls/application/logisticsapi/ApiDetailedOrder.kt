package no.nb.mlt.wls.application.logisticsapi

import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Order.Address
import no.nb.mlt.wls.domain.model.Order.OrderItem
import no.nb.mlt.wls.domain.model.Order.Status
import no.nb.mlt.wls.domain.model.Order.Type
import no.nb.mlt.wls.domain.model.Packaging

/**
 * A DTO carrying the details of an [Order], with the full [Item] details of its order items.
 */
data class ApiDetailedOrder(
    val hostName: HostName,
    val hostOrderId: String,
    val status: Status,
    val orderLine: List<DetailedOrderItem>,
    val orderType: Type,
    val address: Address?,
    val contactPerson: String,
    val contactEmail: String?,
    val note: String?,
    val callbackUrl: String
)

/**
 * A DTO carrying an [Item] from the storage system, with its status for an order.
 *
 * @property hostId The unique identifier for the item in the host system.
 * @property hostName The name of the host system which owns the item.
 * @property description A brief description of the item.
 * @property itemCategory The category of the item.
 * @property preferredEnvironment The preferred storage environment for the item.
 * @property packaging The packaging type of the item.
 * @property callbackUrl An optional URL for callbacks related to the item.
 * @property location The current location of the item.
 * @property quantity The quantity of the item available in stock.
 * @property status The order status of the item, represented by the [OrderItem.Status] enum.
 */
data class DetailedOrderItem(
    val hostId: String,
    val hostName: HostName,
    val description: String,
    val itemCategory: ItemCategory,
    val preferredEnvironment: Environment,
    val packaging: Packaging,
    val callbackUrl: String?,
    val location: String,
    val quantity: Int,
    val status: OrderItem.Status
)

fun Order.toDetailedOrder(items: List<Item>): ApiDetailedOrder {
    val detailedOrderItems = mutableListOf<DetailedOrderItem>()
    items.forEach { item ->
        this.orderLine.forEach { orderItem ->
            if (orderItem.hostId == item.hostId) {
                detailedOrderItems.add(orderItem.toDetailedOrderItem(item))
            }
        }
    }

    return ApiDetailedOrder(
        hostName = this.hostName,
        hostOrderId = this.hostOrderId,
        status = this.status,
        orderLine = detailedOrderItems.toList(),
        orderType = this.orderType,
        address = this.address,
        contactPerson = this.contactPerson,
        contactEmail = this.contactEmail,
        note = this.note,
        callbackUrl = this.callbackUrl
    )
}

fun OrderItem.toDetailedOrderItem(item: Item): DetailedOrderItem =
    DetailedOrderItem(
        hostId = item.hostId,
        hostName = item.hostName,
        description = item.description,
        itemCategory = item.itemCategory,
        preferredEnvironment = item.preferredEnvironment,
        packaging = item.packaging,
        callbackUrl = item.callbackUrl,
        location = item.location,
        quantity = item.quantity,
        status = this.status
    )
