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
                    logger.info { "Item ${changedItem.hostId} - ${changedItem.hostName} was edited with new metadata: {$newMetadata}" }
                    storageEventRepository.save(
                        ItemEdited(
                            EditedItemInfo(editedItem = changedItem, oldItem = item)
                        )
                    )
                } else {
                    logger.info { "Item ${changedItem.hostId} - ${changedItem.hostName} was unchanged after edit with new metadata: {$newMetadata}" }
                    null
                }
            }

        processStorageEventAsync(storageEvent)

        // Return item from DB to ensure we return the actual item info we persisted
        return getItemOrThrow(changedItem.hostName, changedItem.hostId)
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
        val catalogEvent =
            transactionPort.executeInTransaction {
                if (itemRepository.moveItem(movedItem)) {
                    logger.info { "Moved ${movedItem.quantity} of item ${originalItem.hostId} - ${originalItem.hostName} to ${movedItem.location}" }
                    catalogEventRepository.save(ItemEvent(movedItem))
                } else {
                    logger.info {
                        "Move of ${movedItem.quantity} items to ${movedItem.location} had no effect on ${originalItem.hostId} - ${originalItem.hostName}"
                    }
                    null
                }
            }

        processCatalogEventAsync(catalogEvent)

        if (catalogEvent != null && originalItem.quantity == 0 && movedItem.quantity > 0) {
            returnOrderItems(movedItem.hostName, listOf(movedItem.hostId))
        }

        return getItemOrThrow(movedItem.hostName, movedItem.hostId)
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
            throw ItemNotFoundException("Some items do not exist in the database, and so are unpickable: ${pickedItems.keys.joinToString(", ")}")
        }

        val itemsToPick = getItemsByIds(hostName, pickedItems.keys.toList())

        itemsToPick.forEach { item ->
            val pickedItemsQuantity = pickedItems[item.hostId] ?: 0
            val pickedItem = item.pick(pickedItemsQuantity)

            // Can't we just replace the rest of this block with a call to the `moveItemInternal` function now?

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
            }.forEach {
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

        val catalogEvent =
            transactionPort.executeInTransaction {
                if (orderRepository.updateOrder(pickedOrder)) {
                    logger.info { "Order $orderId was picked with items $pickedItemIds for it" }
                    catalogEventRepository.save(OrderEvent(pickedOrder))
                } else {
                    logger.warn { "Order $orderId was unchanged after picking items $pickedItemIds for it" }
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
                // This should return a boolean
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

        val catalogEvent =
            transactionPort.executeInTransaction {
                if (orderRepository.updateOrder(updatedOrder)) {
                    logger.info { "Order $hostOrderId for $hostName was updated from status ${order.status} to $status" }
                    catalogEventRepository.save(OrderEvent(updatedOrder))
                } else {
                    logger.warn { "Order $hostOrderId for $hostName was unchanged after status update from ${order.status} to $status" }
                    null
                }
            }

        processCatalogEventAsync(catalogEvent)

        return getOrderOrThrow(hostName, hostOrderId)
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

    private fun processStorageEventAsync(storageEvent: StorageEvent?) {
        if (storageEvent == null) {
            logger.debug { "Storage event was null, ignoring" }
            return
        }

        coroutineContext.launch {
            try {
                storageEventProcessor.handleEvent(storageEvent)
                logger.info { "Storage event $storageEvent was processed successfully" }
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
    }

    private fun processCatalogEventAsync(catalogEvent: CatalogEvent?) {
        if (catalogEvent == null) {
            logger.debug { "Catalog event was null, ignoring" }
            return
        }

        coroutineContext.launch {
            try {
                catalogEventProcessor.handleEvent(catalogEvent)
                logger.info { "Catalog event $catalogEvent was processed successfully" }
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
    }

    private suspend fun returnOrderItems(
        hostName: HostName,
        returnedItems: List<String>
    ) {
        val orders: List<Order> = orderRepository.getOrdersWithPickedItems(hostName, returnedItems)

        orders.forEach { order ->
            val returnedOrder = order.returnItems(returnedItems)

            val orderEvent =
                transactionPort.executeInTransaction {
                    if (orderRepository.updateOrder(returnedOrder)) {
                        logger.info { "Order ${returnedOrder.hostOrderId} - ${returnedOrder.hostName} had some of its items returned" }
                        catalogEventRepository.save(OrderEvent(returnedOrder))
                    } else {
                        logger.warn { "Order ${returnedOrder.hostOrderId} - ${returnedOrder.hostName} unchanged after some of its items returned" }
                        null
                    }
                }

            processCatalogEventAsync(orderEvent)
        }
    }

    private suspend fun createMissingItems(
        missingId: String,
        hostName: HostName,
        syncItemsById: Map<Pair<String, HostName>, SynchronizeItems.ItemToSynchronize>
    ) {
        val itemToSynchronize = syncItemsById[(missingId to hostName)]
        val syncItem =
            itemToSynchronize ?: throw NullPointerException("Item to synchronize not found in sync data for hostId: $missingId, hostName: $hostName")

        val createdItem = itemRepository.createItem(syncItem.toItem())
        logger.info { "Item didn't exist when synchronizing. Created item: $createdItem" }
    }

    private suspend fun updateItemsForSynchronization(
        itemToUpdate: Item,
        syncItemsById: Map<Pair<String, HostName>, SynchronizeItems.ItemToSynchronize>
    ) {
        val newItem =
            syncItemsById[(itemToUpdate.hostId to itemToUpdate.hostName)]
                ?: throw NullPointerException(
                    "Item to synchronize not found in sync data for hostId: ${itemToUpdate.hostId}, hostName: ${itemToUpdate.hostName}"
                )
        val syncedItem = itemToUpdate.synchronizeItem(newItem.quantity, newItem.location, newItem.associatedStorage)

        val event =
            transactionPort.executeInTransaction {
                if (itemRepository.moveItem(syncedItem)) {
                    logger.info {
                        """
                        Synchronizing item ${syncedItem.hostId} - ${syncedItem.hostName}:
                        Synchronizing quantity [${itemToUpdate.quantity} -> ${syncedItem.quantity}]
                        Synchronizing location [${itemToUpdate.location} -> ${syncedItem.location}]
                        """.trimIndent()
                    }
                    catalogEventRepository.save(ItemEvent(syncedItem))
                } else {
                    null
                }
            }

        processCatalogEventAsync(event)
    }

    override suspend fun reportItemMissing(
        hostName: HostName,
        hostId: String
    ): Item {
        val item = getItem(hostName, hostId) ?: throw ItemNotFoundException("Could not find item")
        val missingItem = item.reportMissing()

        val catalogItemEvent =
            transactionPort.executeInTransaction {
                if (itemRepository.moveItem(missingItem)) {
                    logger.info { "Item ${missingItem.hostId} - ${missingItem.hostName} was marked as missing" }
                    catalogEventRepository.save(ItemEvent(missingItem))
                } else {
                    logger.warn { "Item ${missingItem.hostId} - ${missingItem.hostName} was unchanged after being marked as missing" }
                    null
                }
            }

        processCatalogEventAsync(catalogItemEvent)
        markOrdersAsMissing(hostName, listOf(hostId))

        return getItemOrThrow(missingItem.hostName, missingItem.hostId)
    }

    private suspend fun markOrdersAsMissing(
        hostName: HostName,
        hostIds: List<String>
    ) {
        val orders = orderRepository.getOrdersWithItems(hostName, hostIds)

        if (orders.isEmpty()) {
            logger.info { "No orders found for $hostName containing $hostIds" }
            return
        }

        orders
            .mapNotNull {
                // remove orders that are unchanged after its items were marked as missing
                val updatedOrder = it.markMissing(hostIds)
                if (updatedOrder != it) updatedOrder else null
            }.forEach { missingOrder ->
                val orderEvent =
                    transactionPort.executeInTransaction {
                        if (orderRepository.updateOrder(missingOrder)) {
                            logger.warn { "Items $hostIds in order: ${missingOrder.hostOrderId} - ${missingOrder.hostName} were marked as missing" }
                            catalogEventRepository.save(OrderEvent(missingOrder))
                        } else {
                            logger.error {
                                "Items $hostIds in order: ${missingOrder.hostOrderId} - ${missingOrder.hostName} were not marked as missing, transaction failed"
                            }
                            null
                        }
                    }

                processCatalogEventAsync(orderEvent)
            }
    }
}
