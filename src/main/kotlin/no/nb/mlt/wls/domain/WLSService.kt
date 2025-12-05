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
import no.nb.mlt.wls.domain.model.UNKNOWN_LOCATION
import no.nb.mlt.wls.domain.model.events.catalog.CatalogEvent
import no.nb.mlt.wls.domain.model.events.catalog.ItemEvent
import no.nb.mlt.wls.domain.model.events.catalog.OrderEvent
import no.nb.mlt.wls.domain.model.events.storage.EditedItemInfo
import no.nb.mlt.wls.domain.model.events.storage.ItemCreated
import no.nb.mlt.wls.domain.model.events.storage.ItemEdited
import no.nb.mlt.wls.domain.model.events.storage.OrderCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderDeleted
import no.nb.mlt.wls.domain.model.events.storage.StorageEvent
import no.nb.mlt.wls.domain.ports.inbound.AddNewItem
import no.nb.mlt.wls.domain.ports.inbound.CreateOrder
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import no.nb.mlt.wls.domain.ports.inbound.DeleteOrder
import no.nb.mlt.wls.domain.ports.inbound.EditItem
import no.nb.mlt.wls.domain.ports.inbound.GetItem
import no.nb.mlt.wls.domain.ports.inbound.GetItems
import no.nb.mlt.wls.domain.ports.inbound.GetOrder
import no.nb.mlt.wls.domain.ports.inbound.GetOrders
import no.nb.mlt.wls.domain.ports.inbound.ItemEditMetadata
import no.nb.mlt.wls.domain.ports.inbound.ItemMetadata
import no.nb.mlt.wls.domain.ports.inbound.MoveItem
import no.nb.mlt.wls.domain.ports.inbound.MoveItemPayload
import no.nb.mlt.wls.domain.ports.inbound.PickItems
import no.nb.mlt.wls.domain.ports.inbound.PickOrderItems
import no.nb.mlt.wls.domain.ports.inbound.ReportItemAsMissing
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
    private val emailService: EmailService,
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
    EditItem,
    UpdateItem,
    UpdateOrderStatus,
    MoveItem,
    PickOrderItems,
    PickItems,
    ReportItemAsMissing,
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
            }

        processStorageEventAsync(storageEvent)
        return createdItem
    }

    override suspend fun editItem(
        item: Item,
        newMetadata: ItemEditMetadata
    ): Item {
        val changedItem =
            item.edit(
                description = newMetadata.description,
                itemCategory = newMetadata.itemCategory,
                preferredEnvironment = newMetadata.preferredEnvironment,
                packaging = newMetadata.packaging,
                callbackUrl = newMetadata.callbackUrl
            )
        val storageEvent =
            transactionPort.executeInTransaction {
                if (itemRepository.editItem(changedItem)) {
                    storageEventRepository.save(
                        ItemEdited(
                            EditedItemInfo(editedItem = changedItem, oldItem = item)
                        )
                    )
                } else {
                    logger.info { "Item ${item.hostId} for ${item.hostName} was not changed" }
                    null
                }
            }
        processStorageEventAsync(storageEvent)
        return changedItem
    }

    override suspend fun updateItem(updateItemPayload: UpdateItemPayload): Item {
        val item = getItemOrThrow(updateItemPayload.hostName, updateItemPayload.hostId)

        val updateItem =
            item.move(
                updateItemPayload.location,
                updateItemPayload.quantity,
                updateItemPayload.associatedStorage
            )

        return moveItemInternal(item, updateItem)
    }

    override suspend fun moveItem(moveItemPayload: MoveItemPayload): Item {
        val item = getItemOrThrow(moveItemPayload.hostName, moveItemPayload.hostId)

        val updateItem =
            item.move(
                moveItemPayload.location,
                item.quantity + moveItemPayload.quantity,
                moveItemPayload.associatedStorage
            )

        return moveItemInternal(item, updateItem)
    }

    private suspend fun moveItemInternal(
        originalItem: Item,
        movedItem: Item
    ): Item {
        if (originalItem == movedItem) {
            logger.info { "Item ${movedItem.hostId} for ${movedItem.hostName} was unchanged" }
            return originalItem
        }

        val catalogEvent =
            transactionPort.executeInTransaction {
                if (itemRepository.moveItem(movedItem)) {
                    catalogEventRepository.save(ItemEvent(movedItem))
                } else {
                    null
                }
            }

        if (catalogEvent != null) {
            processCatalogEventAsync(catalogEvent)
            if (originalItem.quantity == 0 && movedItem.quantity > 0) {
                returnOrderItems(movedItem.hostName, listOf(movedItem.hostId))
            }
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
                    if (itemRepository.moveItem(pickedItem)) {
                        catalogEventRepository.save(ItemEvent(pickedItem))
                    } else {
                        null
                    }
                }

            if (catalogEvent != null) {
                processCatalogEventAsync(catalogEvent)
                logger.debug { "Items picked for $hostName" }
            }
        }
    }

    override suspend fun createOrder(orderDTO: CreateOrderDTO): Order {
        orderRepository.getOrder(orderDTO.hostName, orderDTO.hostOrderId)?.let {
            logger.info { "Order already exists: $it, returning existing order" }
            return it
        }

        val itemIds = orderDTO.orderLine.map { it.hostId }
        val existingItems = itemRepository.getItemsByIds(orderDTO.hostName, itemIds)
        val missingItems = mutableListOf<Item>()
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
                missingItems.addLast(createdItem)
            }

        val (createdOrder, storageEvent) =
            transactionPort.executeInTransaction {
                val createdOrder = orderRepository.createOrder(orderDTO.toOrder())
                val storageEvent = storageEventRepository.save(OrderCreated(createdOrder))
                (createdOrder to storageEvent)
            }
        val items = existingItems.plus(missingItems)

        emailService.createOrderConfirmation(createdOrder)
        emailService.createOrderPickup(createdOrder, items)

        processStorageEventAsync(storageEvent)

        return createdOrder
    }

    override suspend fun pickOrderItems(
        hostName: HostName,
        pickedItemIds: List<String>,
        orderId: String
    ) {
        val order = getOrderOrThrow(hostName, orderId)
        val pickedOrder = order.pick(pickedItemIds)
        if (order == pickedOrder) {
            logger.warn { "Order was unchanged after picking items $pickedItemIds from $order" }
            return
        }

        val catalogEvent =
            transactionPort.executeInTransaction {
                if (orderRepository.updateOrder(pickedOrder)) {
                    val event = catalogEventRepository.save(OrderEvent(pickedOrder))
                    event
                } else {
                    null
                }
            }
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
            }

        processStorageEventAsync(storageEvent)
    }

    override suspend fun updateOrderStatus(
        hostName: HostName,
        hostOrderId: String,
        status: Order.Status
    ): Order {
        val order = getOrderOrThrow(hostName, hostOrderId)
        val updatedOrder = order.updateStatus(status)
        if (order == updatedOrder) {
            logger.info { "Tried to update order $hostOrderId for $hostName with same status as it was before" }
            return order
        }
        val (result, catalogEvent) =
            transactionPort.executeInTransaction {
                if (orderRepository.updateOrder(updatedOrder)) {
                    val catalogEvent = catalogEventRepository.save(OrderEvent(updatedOrder))
                    (updatedOrder to catalogEvent)
                } else {
                    (updatedOrder to null)
                }
            }

        processCatalogEventAsync(catalogEvent)

        return result
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

    override suspend fun getItemsById(hostId: String): List<Item> = itemRepository.getItemsById(hostId)

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

    @Throws(ItemNotFoundException::class)
    private suspend fun getItemOrThrow(
        hostName: HostName,
        hostId: String
    ): Item =
        getItem(hostName, hostId)
            ?: throw ItemNotFoundException("Item with id '$hostId' does not exist for '$hostName'")

    private fun processStorageEventAsync(storageEvent: StorageEvent?) =
        coroutineContext.launch {
            if (storageEvent == null) {
                logger.debug { "Storage event was null, ignoring" }
                return@launch
            }
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

    private fun processCatalogEventAsync(catalogEvent: CatalogEvent?) =
        coroutineContext.launch {
            if (catalogEvent == null) {
                logger.debug { "Catalog event was null, ignoring" }
                return@launch
            }
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
                    if (orderRepository.updateOrder(returnOrder)) {
                        val orderEvent = catalogEventRepository.save(OrderEvent(returnOrder))
                        (returnOrder to orderEvent)
                    } else {
                        null
                    }
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
                    location = syncItem.location ?: UNKNOWN_LOCATION,
                    quantity = syncItem.quantity,
                    associatedStorage = syncItem.associatedStorage
                )
            )
        logger.info { "Item didn't exist when synchronizing. Created item: $createdItem" }
    }

    private suspend fun updateItemsForSynchronization(
        itemToUpdate: Item,
        syncItemsById: Map<Pair<String, HostName>, SynchronizeItems.ItemToSynchronize>
    ) {
        val itemToSynchronize = syncItemsById[(itemToUpdate.hostId to itemToUpdate.hostName)]!!

        val oldQuantity = itemToUpdate.quantity
        val oldLocation = itemToUpdate.location

        val syncedItem = itemToUpdate.synchronizeItem(itemToSynchronize.quantity, itemToSynchronize.location, itemToSynchronize.associatedStorage)

        if (oldQuantity != syncedItem.quantity || oldLocation != syncedItem.location) {
            val event =
                transactionPort.executeInTransaction {
                    if (itemRepository.moveItem(syncedItem)) {
                        logger.info {
                            """
                            Synchronizing item ${syncedItem.hostName}_${syncedItem.hostId}:
                            Synchronizing quantity [$oldQuantity -> ${syncedItem.quantity}]
                            Synchronizing location [$oldLocation -> ${syncedItem.location}]
                            """.trimIndent()
                        }
                        catalogEventRepository.save(ItemEvent(syncedItem))
                    } else {
                        null
                    }
                }
            processCatalogEventAsync(event)
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
                if (itemRepository.moveItem(missingItem)) {
                    val event = catalogEventRepository.save(ItemEvent(missingItem))
                    (missingItem to event)
                } else {
                    // TODO - Exception?
                    (missingItem to null)
                }
            }
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
                val updatedOrder = it.markMissing(hostIds)
                if (updatedOrder != it) updatedOrder else null
            }.forEach { missingOrder ->
                val orderEvent =
                    transactionPort.executeInTransaction {
                        if (orderRepository.updateOrder(missingOrder)) {
                            catalogEventRepository.save(OrderEvent(missingOrder))
                        } else {
                            null
                        }
                    }

                if (orderEvent == null) {
                    logger.error {
                        "Failed to properly mark order: ${missingOrder.hostOrderId} in host: ${missingOrder.hostName} as missing, transaction failed"
                    }
                    return@forEach
                }

                logger.warn {
                    "Order line(s) $hostIds in ${missingOrder.hostOrderId} for ${missingOrder.hostName} was marked as missing"
                }
                processCatalogEventAsync(orderEvent)
            }
    }
}
