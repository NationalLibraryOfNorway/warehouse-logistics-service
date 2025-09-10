package no.nb.mlt.wls.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.model.events.catalog.CatalogEvent
import no.nb.mlt.wls.domain.model.events.catalog.ItemEvent
import no.nb.mlt.wls.domain.model.events.catalog.OrderEvent
import no.nb.mlt.wls.domain.model.events.storage.ItemCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderDeleted
import no.nb.mlt.wls.domain.model.events.storage.StorageEvent
import no.nb.mlt.wls.domain.ports.inbound.AddNewItem
import no.nb.mlt.wls.domain.ports.inbound.CreateOrder
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import no.nb.mlt.wls.domain.ports.inbound.DeleteOrder
import no.nb.mlt.wls.domain.ports.inbound.GetItem
import no.nb.mlt.wls.domain.ports.inbound.GetItems
import no.nb.mlt.wls.domain.ports.inbound.GetOrder
import no.nb.mlt.wls.domain.ports.inbound.GetOrders
import no.nb.mlt.wls.domain.ports.inbound.ItemMetadata
import no.nb.mlt.wls.domain.ports.inbound.MoveItem
import no.nb.mlt.wls.domain.ports.inbound.MoveItemPayload
import no.nb.mlt.wls.domain.ports.inbound.PickItems
import no.nb.mlt.wls.domain.ports.inbound.PickOrderItems
import no.nb.mlt.wls.domain.ports.inbound.ReportItemAsMissing
import no.nb.mlt.wls.domain.ports.inbound.StockCount
import no.nb.mlt.wls.domain.ports.inbound.SynchronizeItems
import no.nb.mlt.wls.domain.ports.inbound.UpdateItem
import no.nb.mlt.wls.domain.ports.inbound.UpdateItem.UpdateItemPayload
import no.nb.mlt.wls.domain.ports.inbound.UpdateOrderStatus
import no.nb.mlt.wls.domain.ports.inbound.exceptions.ItemNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.exceptions.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.outbound.EventProcessor
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository.ItemId
import no.nb.mlt.wls.domain.ports.outbound.OrderRepository
import no.nb.mlt.wls.domain.ports.outbound.TransactionPort
import no.nb.mlt.wls.domain.ports.outbound.exceptions.DuplicateResourceException
import no.nb.mlt.wls.domain.ports.outbound.exceptions.StorageSystemException
import org.springframework.web.reactive.function.client.WebClientResponseException

private val logger = KotlinLogging.logger {}

class WLSService(
    private val itemRepository: ItemRepository,
    private val orderRepository: OrderRepository,
    private val catalogEventRepository: EventRepository<CatalogEvent>,
    private val storageEventRepository: EventRepository<StorageEvent>,
    private val transactionPort: TransactionPort,
    private val catalogEventProcessor: EventProcessor<CatalogEvent>,
    private val storageEventProcessor: EventProcessor<StorageEvent>
) : AddNewItem,
    CreateOrder,
    DeleteOrder,
    GetOrder,
    GetOrders,
    GetItem,
    GetItems,
    UpdateItem,
    UpdateOrderStatus,
    MoveItem,
    PickOrderItems,
    PickItems,
    ReportItemAsMissing,
    StockCount,
    SynchronizeItems {
    private val coroutineContext = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun addItem(itemMetadata: ItemMetadata): Item {
        getItem(itemMetadata.hostName, itemMetadata.hostId)?.let {
            logger.info { "Item already exists: $it" }
            return it
        }

        val (createdItem, storageEvent) =
            transactionPort.executeInTransaction {
                val createdItem = itemRepository.createItem(itemMetadata.toItem())
                val event = storageEventRepository.save(ItemCreated(createdItem))

                (createdItem to event)
            } ?: throw RuntimeException("Could not create item")

        processStorageEventAsync(storageEvent)
        return createdItem
    }

    override suspend fun updateItem(updateItemPayload: UpdateItemPayload): Item {
        val item =
            getItem(updateItemPayload.hostName, updateItemPayload.hostId)
                ?: throw ItemNotFoundException("Item with id '${updateItemPayload.hostId}' does not exist for '${updateItemPayload.hostName}'")

        val (updatedItem, catalogEvent) =
            transactionPort.executeInTransaction {
                val updatedItem =
                    itemRepository.updateLocationAndQuantity(
                        item.hostId,
                        item.hostName,
                        updateItemPayload.location,
                        updateItemPayload.quantity
                    )
                val event = catalogEventRepository.save(ItemEvent(updatedItem))

                (updatedItem to event)
            } ?: throw RuntimeException("Could not update item")

        processCatalogEventAsync(catalogEvent)

        if (item.quantity == 0 && updateItemPayload.quantity > 0) {
            returnOrderItems(updateItemPayload.hostName, listOf(updateItemPayload.hostId))
        }
        return updatedItem
    }

    override suspend fun moveItem(moveItemPayload: MoveItemPayload): Item {
        val item =
            getItem(moveItemPayload.hostName, moveItemPayload.hostId)
                ?: throw ItemNotFoundException("Item with id '${moveItemPayload.hostId}' does not exist for '${moveItemPayload.hostName}'")

        val (movedItem, catalogEvent) =
            transactionPort.executeInTransaction {
                val movedItem =
                    itemRepository.moveItem(
                        item.hostName,
                        item.hostId,
                        item.quantity + moveItemPayload.quantity,
                        moveItemPayload.location
                    )
                val event = catalogEventRepository.save(ItemEvent(movedItem))

                (movedItem to event)
            } ?: throw RuntimeException("Could not move item")

        processCatalogEventAsync(catalogEvent)

        if (item.quantity == 0 && moveItemPayload.quantity > 0) {
            returnOrderItems(moveItemPayload.hostName, listOf(moveItemPayload.hostId))
        }
        return movedItem
    }

    override suspend fun pickItems(
        hostName: HostName,
        pickedItems: Map<String, Int>
    ) {
        val itemIds =
            pickedItems.map {
                ItemId(hostName, it.key)
            }

        if (!itemRepository.doesEveryItemExist(itemIds)) {
            throw ItemNotFoundException("Some items do not exist in the database, and were unable to be picked")
        }

        val itemsToPick = getItemsByIds(hostName, pickedItems.keys.toList())
        itemsToPick.map { item ->
            val pickedItemsQuantity = pickedItems[item.hostId] ?: 0
            val pickedItem = item.pick(pickedItemsQuantity)

            // Picking an item is guaranteed to set quantity or location.
            // An exception is thrown otherwise
            val catalogEvent =
                transactionPort.executeInTransaction {
                    val movedItem =
                        itemRepository.moveItem(
                            item.hostName,
                            item.hostId,
                            pickedItem.quantity,
                            pickedItem.location
                        )

                    catalogEventRepository.save(ItemEvent(movedItem))
                } ?: throw RuntimeException("Could not move item")

            processCatalogEventAsync(catalogEvent)
        }

        logger.debug { "Items picked for $hostName" }
    }

    override suspend fun createOrder(orderDTO: CreateOrderDTO): Order {
        orderRepository.getOrder(orderDTO.hostName, orderDTO.hostOrderId)?.let {
            logger.info { "Order already exists: $it, returning existing order" }
            return it
        }

        val itemIds = orderDTO.orderLine.map { it.hostId }
        val existingItems = itemRepository.getItemsByIds(orderDTO.hostName, itemIds)

        // Create missing items
        itemIds
            .filter {
                existingItems.none { existingItem ->
                    existingItem.hostId == it
                }
            }.map {
                val createdItem =
                    addItem(
                        ItemMetadata(
                            hostName = orderDTO.hostName,
                            hostId = it,
                            description = "NO DESCRIPTION",
                            itemCategory = ItemCategory.UNKNOWN,
                            preferredEnvironment = Environment.NONE,
                            packaging = Packaging.UNKNOWN,
                            callbackUrl = null
                        )
                    )
                logger.info { "Created unknown item: $createdItem, from order: ${orderDTO.hostOrderId}" }
            }

        val (createdOrder, storageEvent) =
            transactionPort.executeInTransaction {
                val createdOrder = orderRepository.createOrder(orderDTO.toOrder())
                val storageEvent = storageEventRepository.save(OrderCreated(createdOrder))

                (createdOrder to storageEvent)
            } ?: throw RuntimeException("Could not create order")

        processStorageEventAsync(storageEvent)

        return createdOrder
    }

    override suspend fun pickOrderItems(
        hostName: HostName,
        pickedItemIds: List<String>,
        orderId: String
    ) {
        val order = getOrderOrThrow(hostName, orderId)

        val catalogEvent =
            transactionPort.executeInTransaction {
                val pickedOrder = orderRepository.updateOrder(order.pick(pickedItemIds))

                catalogEventRepository.save(OrderEvent(pickedOrder))
            } ?: throw RuntimeException("Could not pick order items")

        processCatalogEventAsync(catalogEvent)
    }

    override suspend fun deleteOrder(
        hostName: HostName,
        hostOrderId: String
    ) {
        val deletedOrder = getOrderOrThrow(hostName, hostOrderId).delete()
        val storageEvent =
            transactionPort.executeInTransaction {
                orderRepository.deleteOrder(deletedOrder)
                storageEventRepository.save(OrderDeleted(deletedOrder.hostName, deletedOrder.hostOrderId))
            } ?: throw RuntimeException("Could not delete order")

        processStorageEventAsync(storageEvent)
    }

    override suspend fun updateOrderStatus(
        hostName: HostName,
        hostOrderId: String,
        status: Order.Status
    ): Order {
        val order = getOrderOrThrow(hostName, hostOrderId)

        val (updatedOrder, catalogEvent) =
            transactionPort.executeInTransaction {
                val updatedOrder = orderRepository.updateOrder(order.updateStatus(status))
                val catalogEvent = catalogEventRepository.save(OrderEvent(updatedOrder))

                (updatedOrder to catalogEvent)
            } ?: throw RuntimeException("Could not update order status")

        processCatalogEventAsync(catalogEvent)

        return updatedOrder
    }

    override suspend fun getItem(
        hostName: HostName,
        hostId: String
    ): Item? = itemRepository.getItem(hostName, hostId)

    override suspend fun getAllItems(hostnames: List<HostName>): List<Item> = itemRepository.getAllItemsForHosts(hostnames)

    override suspend fun getItemsByIds(
        hostName: HostName,
        hostIds: List<String>
    ): List<Item> = itemRepository.getItemsByIds(hostName, hostIds)

    override suspend fun getOrder(
        hostName: HostName,
        hostOrderId: String
    ): Order? = orderRepository.getOrder(hostName, hostOrderId)

    override suspend fun getAllOrders(hostnames: List<HostName>): List<Order> = orderRepository.getAllOrdersForHosts(hostnames)

    override suspend fun getOrdersById(
        hostNames: List<HostName>,
        hostOrderId: String
    ): List<Order> = orderRepository.getAllOrdersWithHostId(hostNames, hostOrderId)

    override suspend fun synchronizeItems(items: List<SynchronizeItems.ItemToSynchronize>) {
        val syncItemsById = items.associateBy { (it.hostId to it.hostName) }
        items
            .groupBy { it.hostName }
            .forEach { (hostName, syncItems) ->
                transactionPort.executeInTransaction {
                    val ids = syncItems.map { it.hostId }
                    val existingItems = getItemsByIds(hostName, ids)
                    val existingIds = existingItems.map { it.hostId }
                    val missingIds = ids.toSet() - existingIds.toSet()

                    missingIds.forEach { createMissingItems(it, hostName, syncItemsById) }
                    existingItems.forEach { updateItemsForSynchronization(it, syncItemsById) }
                }
            }
    }

    @Throws(OrderNotFoundException::class)
    private suspend fun getOrderOrThrow(
        hostName: HostName,
        hostOrderId: String
    ): Order =
        getOrder(
            hostName,
            hostOrderId
        ) ?: throw OrderNotFoundException("No order with hostOrderId: $hostOrderId and hostName: $hostName exists")

    private fun processStorageEventAsync(storageEvent: StorageEvent) =
        coroutineContext.launch {
            try {
                storageEventProcessor.handleEvent(storageEvent)
            } catch (e: StorageSystemException) {
                logger.error(e) {
                    "Storage system reported error while processing outbox event: $storageEvent. Try again later"
                }
            } catch (e: DuplicateResourceException) {
                logger.warn(e) {
                    "Outbox event produced a duplicate resource. Event: $storageEvent"
                }
            } catch (e: Exception) {
                logger.error(e) {
                    "Unexpected error while processing outbox event: $storageEvent. Try again later"
                }
            }
        }

    private fun processCatalogEventAsync(catalogEvent: CatalogEvent) =
        coroutineContext.launch {
            try {
                catalogEventProcessor.handleEvent(catalogEvent)
            } catch (e: WebClientResponseException) {
                logger.error(e) {
                    "Web client threw an error while sending out: $catalogEvent. Try again later"
                }
            } catch (e: Exception) {
                logger.error(e) {
                    "Unexpected error while processing outbox message: $catalogEvent. Try again later"
                }
            }
        }

    private suspend fun returnOrderItems(
        hostName: HostName,
        returnedItems: List<String>
    ) {
        val orders: List<Order> = orderRepository.getOrdersWithPickedItems(hostName, returnedItems)

        orders.forEach { order ->
            val (returnedOrder, orderEvent) =
                transactionPort.executeInTransaction {
                    val returnOrder = order.returnItems(returnedItems)
                    val updatedOrder = orderRepository.updateOrder(returnOrder)
                    val orderEvent = catalogEventRepository.save(OrderEvent(updatedOrder))
                    (updatedOrder to orderEvent)
                } ?: (order to null)

            if (orderEvent == null) {
                logger.error {
                    "Failed to properly mark order: ${returnedOrder.hostOrderId} in host: ${returnedOrder.hostName} as returned, transaction failed"
                }
                return@forEach
            }

            processCatalogEventAsync(orderEvent)
        }
    }

    private suspend fun createMissingItems(
        missingId: String,
        hostName: HostName,
        syncItemsById: Map<Pair<String, HostName>, SynchronizeItems.ItemToSynchronize>
    ) {
        val syncItem = syncItemsById[(missingId to hostName)]!!
        val createdItem =
            itemRepository.createItem(
                Item(
                    hostId = syncItem.hostId,
                    hostName = syncItem.hostName,
                    description = syncItem.description,
                    itemCategory = syncItem.itemCategory,
                    preferredEnvironment = syncItem.currentPreferredEnvironment,
                    packaging = syncItem.packaging,
                    callbackUrl = null,
                    location = syncItem.location,
                    quantity = syncItem.quantity
                )
            )
        logger.info { "Item didn't exist when synchronizing. Created item: $createdItem" }
    }

    private suspend fun updateItemsForSynchronization(
        itemToUpdate: Item,
        syncItemsById: Map<Pair<String, HostName>, SynchronizeItems.ItemToSynchronize>
    ) {
        val syncItem = syncItemsById[(itemToUpdate.hostId to itemToUpdate.hostName)]!!

        val oldQuantity = itemToUpdate.quantity
        val oldLocation = itemToUpdate.location

        itemToUpdate.synchronizeQuantityAndLocation(syncItem.quantity, syncItem.location)

        if (oldQuantity != itemToUpdate.quantity || oldLocation != itemToUpdate.location) {
            itemRepository.updateLocationAndQuantity(
                itemToUpdate.hostId,
                itemToUpdate.hostName,
                itemToUpdate.location,
                itemToUpdate.quantity
            )
            logger.info {
                """
                Synchronizing item ${itemToUpdate.hostName}_${itemToUpdate.hostId}:
                Synchronizing quantity [$oldQuantity -> ${itemToUpdate.quantity}]
                Synchronizing location [$oldLocation -> ${itemToUpdate.location}]
                """.trimIndent()
            }
        }
    }

    override suspend fun countStock(items: List<StockCount.CountStockDTO>) {
        val updatedItemMap = items.associateBy { (it.hostId to it.hostName) }
        val currentItems = getItemsByIds(items.first().hostName, items.map { it.hostId })

        currentItems.forEach { item ->
            val updatedItem = updatedItemMap[item.hostId to item.hostName]
            if (updatedItem != null) {
                itemRepository.updateLocationAndQuantity(updatedItem.hostId, updatedItem.hostName, updatedItem.location, updatedItem.quantity)
                logger.info {
                    """
                    Updated stock of item item ${updatedItem.hostName}_${updatedItem.hostId}:
                    Updated quantity [${item.quantity} -> ${updatedItem.quantity}]
                    Updated location [${item.location} -> ${updatedItem.location}]
                    """.trimIndent()
                }
            } else {
                logger.trace { "Item ${item.hostId} for ${item.hostName} not found" }
            }
        }
    }

    override suspend fun reportItemMissing(
        hostName: HostName,
        hostId: String
    ): Item {
        val item = getItem(hostName, hostId) ?: throw ItemNotFoundException("Could not find item")
        val (missingItem, catalogItemEvent) =
            transactionPort.executeInTransaction {
                val missingItem = item.reportMissing()
                val updatedMissingItem =
                    itemRepository.updateLocationAndQuantity(
                        missingItem.hostId,
                        missingItem.hostName,
                        missingItem.location,
                        missingItem.quantity
                    )
                val event = catalogEventRepository.save(ItemEvent(updatedMissingItem))
                (updatedMissingItem to event)
            } ?: throw RuntimeException("Unexpected error when updating item")

        processCatalogEventAsync(catalogItemEvent)
        markOrdersAsMissing(hostName, listOf(hostId))

        return missingItem
    }

    private suspend fun markOrdersAsMissing(
        hostName: HostName,
        hostIds: List<String>
    ) {
        val orders = orderRepository.getOrdersWithItems(hostName, hostIds)
        if (orders.isEmpty()) {
            logger.info {
                "No orders found for $hostName containing $hostIds"
            }
        }
        orders
            .mapNotNull {
                val returnOrder = it.markMissing(hostIds)
                if (returnOrder != it) returnOrder else null
            }.forEach { order ->
                val (_, orderEvent) =
                    transactionPort.executeInTransaction {
                        val updatedOrder = orderRepository.updateOrder(order)
                        val orderEvent = catalogEventRepository.save(OrderEvent(updatedOrder))
                        (updatedOrder to orderEvent)
                    } ?: (order to null)

                if (orderEvent == null) {
                    logger.error {
                        "Failed to properly mark order: ${order.hostOrderId} in host: ${order.hostName} as missing, transaction failed"
                    }
                    return@forEach
                }

                logger.warn {
                    "Order line(s) $hostIds in ${order.hostOrderId} for ${order.hostName} was marked as missing"
                }
                processCatalogEventAsync(orderEvent)
            }
    }
}
