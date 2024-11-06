package no.nb.mlt.wls.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.AddNewItem
import no.nb.mlt.wls.domain.ports.inbound.CreateOrder
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import no.nb.mlt.wls.domain.ports.inbound.DeleteOrder
import no.nb.mlt.wls.domain.ports.inbound.GetItem
import no.nb.mlt.wls.domain.ports.inbound.GetOrder
import no.nb.mlt.wls.domain.ports.inbound.ItemMetadata
import no.nb.mlt.wls.domain.ports.inbound.ItemNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.MoveItem
import no.nb.mlt.wls.domain.ports.inbound.MoveItemPayload
import no.nb.mlt.wls.domain.ports.inbound.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.OrderStatusUpdate
import no.nb.mlt.wls.domain.ports.inbound.PickItems
import no.nb.mlt.wls.domain.ports.inbound.PickOrderItems
import no.nb.mlt.wls.domain.ports.inbound.ServerException
import no.nb.mlt.wls.domain.ports.inbound.UpdateOrder
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import no.nb.mlt.wls.domain.ports.inbound.toItem
import no.nb.mlt.wls.domain.ports.inbound.toOrder
import no.nb.mlt.wls.domain.ports.outbound.DuplicateResourceException
import no.nb.mlt.wls.domain.ports.outbound.InventoryNotifier
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository.ItemId
import no.nb.mlt.wls.domain.ports.outbound.OrderRepository
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import java.time.Duration
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

class WLSService(
    private val itemRepository: ItemRepository,
    private val orderRepository: OrderRepository,
    private val storageSystemFacade: StorageSystemFacade,
    private val inventoryNotifier: InventoryNotifier
) : AddNewItem, CreateOrder, DeleteOrder, UpdateOrder, GetOrder, GetItem, OrderStatusUpdate, MoveItem, PickOrderItems, PickItems {
    override suspend fun addItem(itemMetadata: ItemMetadata): Item {
        getItem(itemMetadata.hostName, itemMetadata.hostId)?.let {
            logger.info { "Item already exists: $it" }
            return it
        }

        val item = itemMetadata.toItem()
        // TODO - Should we handle the case where the item is saved in storage system but not in WLS database?
        storageSystemFacade.createItem(item)
        return itemRepository.createItem(item)
            // TODO - See if timeouts can be made configurable
            .timeout(Duration.ofSeconds(6))
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while saving to WLS database, but saved in storage system. item: $itemMetadata"
                }
            }
            .awaitSingle()
    }

    override suspend fun moveItem(moveItemPayload: MoveItemPayload): Item {
        moveItemPayload.validate()

        val item =
            getItem(moveItemPayload.hostName, moveItemPayload.hostId)
                ?: throw ItemNotFoundException("Item with id '${moveItemPayload.hostId}' does not exist for '${moveItemPayload.hostName}'")

        val movedItem = itemRepository.moveItem(item.hostId, item.hostName, moveItemPayload.quantity, moveItemPayload.location)
        inventoryNotifier.itemChanged(movedItem)
        return movedItem
    }

    override suspend fun pickItems(
        hostName: HostName,
        itemsPickedMap: Map<String, Int>
    ) {
        val itemIds =
            itemsPickedMap.map {
                ItemId(hostName, it.key)
            }

        if (!itemRepository.doesEveryItemExist(itemIds)) {
            throw ItemNotFoundException("Some items do not exist in the database, and were unable to be picked")
        }

        val itemsToPick = getItems(itemsPickedMap.keys.toList(), hostName)
        itemsToPick.map { item ->
            val itemsInStockQuantity = item.quantity ?: 0
            val pickedItemsQuantity = itemsPickedMap[item.hostId] ?: 0

            // In the case of over-picking, set quantity to zero
            // so that on return the database hopefully recovers
            if (pickedItemsQuantity > itemsInStockQuantity) {
                logger.error {
                    """Tried to pick too many items for item with id '${item.hostId}'.
                        |WLS DB has $itemsInStockQuantity stocked, and storage system tried to pick $pickedItemsQuantity
                    """.trimMargin()
                }
            }

            val pickedItem = item.pickItem(pickedItemsQuantity)
            // Picking an item is guaranteed to set quantity or location.
            // An exception is thrown otherwise
            val movedItem =
                itemRepository.moveItem(
                    item.hostId,
                    item.hostName,
                    pickedItem.quantity!!,
                    pickedItem.location!!
                )

            inventoryNotifier.itemChanged(movedItem)
        }
        // TODO - Should we log this once completed?
    }

    override suspend fun pickOrderItems(
        hostName: HostName,
        pickedHostIds: List<String>,
        orderId: String
    ) {
        val order = getOrderOrThrow(hostName, orderId)
        val pickedOrder = orderRepository.updateOrder(order.pickOrder(pickedHostIds))
        inventoryNotifier.orderChanged(pickedOrder)
    }

    override suspend fun createOrder(orderDTO: CreateOrderDTO): Order {
        orderRepository.getOrder(orderDTO.hostName, orderDTO.hostOrderId)?.let {
            logger.info { "Order already exists: $it" }
            return it
        }

        val itemIds = orderDTO.orderLine.map { ItemId(orderDTO.hostName, it.hostId) }
        if (!itemRepository.doesEveryItemExist(itemIds)) {
            throw ValidationException("All order items in order must exist")
        }

        try {
            storageSystemFacade.createOrder(orderDTO.toOrder())
        } catch (e: DuplicateResourceException) {
            // TODO: Should we recover by updating the DB?
            throw ServerException("Order already exists in storage system but not in DB", e)
        }

        val order = orderRepository.createOrder(orderDTO.toOrder())
        return order
    }

    override suspend fun deleteOrder(
        hostName: HostName,
        hostOrderId: String
    ) {
        val order = getOrderOrThrow(hostName, hostOrderId)
        order.deleteOrder()
        storageSystemFacade.deleteOrder(order)
        orderRepository.deleteOrder(hostName, hostOrderId)
    }

    override suspend fun updateOrder(
        hostName: HostName,
        hostOrderId: String,
        itemHostIds: List<String>,
        orderType: Order.Type,
        receiver: Order.Receiver,
        callbackUrl: String
    ): Order {
        val itemIds = itemHostIds.map { ItemId(hostName, it) }
        if (!itemRepository.doesEveryItemExist(itemIds)) {
            throw ValidationException("All order items in order must exist")
        }

        val order = getOrderOrThrow(hostName, hostOrderId)

        val updatedOrder = order.updateOrder(itemHostIds, callbackUrl, orderType, receiver)
        val result = storageSystemFacade.updateOrder(updatedOrder)
        return orderRepository.updateOrder(result)
    }

    override suspend fun getOrder(
        hostName: HostName,
        hostOrderId: String
    ): Order? {
        return orderRepository.getOrder(hostName, hostOrderId)
    }

    override suspend fun getItem(
        hostName: HostName,
        hostId: String
    ): Item? {
        return itemRepository.getItem(hostName, hostId)
    }

    private suspend fun getItems(
        hostIds: List<String>,
        hostName: HostName
    ): List<Item> {
        return itemRepository.getItems(hostIds, hostName)
    }

    override suspend fun updateOrderStatus(
        hostName: HostName,
        hostOrderId: String,
        status: Order.Status
    ): Order {
        val order =
            orderRepository.getOrder(hostName, hostOrderId)
                ?: throw OrderNotFoundException("No order with hostName: $hostName and hostOrderId: $hostOrderId exists")

        val updatedOrder = orderRepository.updateOrder(order.copy(status = status))

        inventoryNotifier.orderChanged(updatedOrder)

        return updatedOrder
    }

    @Throws(OrderNotFoundException::class)
    suspend fun getOrderOrThrow(
        hostName: HostName,
        hostOrderId: String
    ): Order {
        return getOrder(
            hostName,
            hostOrderId
        ) ?: throw OrderNotFoundException("No order with hostOrderId: $hostOrderId and hostName: $hostName exists")
    }
}
