package no.nb.mlt.wls.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.catalogEvents.CatalogEvent
import no.nb.mlt.wls.domain.model.catalogEvents.ItemEvent
import no.nb.mlt.wls.domain.model.catalogEvents.OrderEvent
import no.nb.mlt.wls.domain.model.storageEvents.ItemCreated
import no.nb.mlt.wls.domain.model.storageEvents.OrderCreated
import no.nb.mlt.wls.domain.model.storageEvents.OrderDeleted
import no.nb.mlt.wls.domain.model.storageEvents.OrderUpdated
import no.nb.mlt.wls.domain.model.storageEvents.StorageEvent
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
import no.nb.mlt.wls.domain.ports.inbound.SynchronizeItems
import no.nb.mlt.wls.domain.ports.inbound.UpdateOrder
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import no.nb.mlt.wls.domain.ports.inbound.toItem
import no.nb.mlt.wls.domain.ports.inbound.toOrder
import no.nb.mlt.wls.domain.ports.outbound.DuplicateResourceException
import no.nb.mlt.wls.domain.ports.outbound.EventProcessor
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository.ItemId
import no.nb.mlt.wls.domain.ports.outbound.OrderRepository
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemException
import no.nb.mlt.wls.domain.ports.outbound.TransactionPort
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
) : AddNewItem, CreateOrder, DeleteOrder, UpdateOrder, GetOrder, GetItem, OrderStatusUpdate, MoveItem, PickOrderItems, PickItems, SynchronizeItems {
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

    override suspend fun moveItem(moveItemPayload: MoveItemPayload): Item {
        moveItemPayload.validate()

        val item =
            getItem(moveItemPayload.hostName, moveItemPayload.hostId)
                ?: throw ItemNotFoundException("Item with id '${moveItemPayload.hostId}' does not exist for '${moveItemPayload.hostName}'")

        val (movedItem, catalogEvent) =
            transactionPort.executeInTransaction {
                val movedItem = itemRepository.moveItem(item.hostName, item.hostId, moveItemPayload.quantity, moveItemPayload.location)
                val event = catalogEventRepository.save(ItemEvent(movedItem))

                (movedItem to event)
            } ?: throw RuntimeException("Could not move item")

        processCatalogEventAsync(catalogEvent)
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
            val pickedItemsQuantity = itemsPickedMap[item.hostId] ?: 0
            val pickedItem = item.pickItem(pickedItemsQuantity)

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

        logger.debug {
            "Items picked for $hostName"
        }
    }

    override suspend fun pickOrderItems(
        hostName: HostName,
        pickedHostIds: List<String>,
        orderId: String
    ) {
        val order = getOrderOrThrow(hostName, orderId)

        val catalogEvent =
            transactionPort.executeInTransaction {
                val pickedOrder = orderRepository.updateOrder(order.pickOrder(pickedHostIds))

                catalogEventRepository.save(OrderEvent(pickedOrder))
            } ?: throw RuntimeException("Could not pick order items")

        processCatalogEventAsync(catalogEvent)
    }

    override suspend fun createOrder(orderDTO: CreateOrderDTO): Order {
        orderRepository.getOrder(orderDTO.hostName, orderDTO.hostOrderId)?.let {
            logger.info { "Order already exists: $it, returning existing order" }
            return it
        }

        val itemIds = orderDTO.orderLine.map { ItemId(orderDTO.hostName, it.hostId) }
        if (!itemRepository.doesEveryItemExist(itemIds)) {
            throw ValidationException("All order items in order must exist")
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

    override suspend fun deleteOrder(
        hostName: HostName,
        hostOrderId: String
    ) {
        val order = getOrderOrThrow(hostName, hostOrderId).deleteOrder()
        val storageEvent =
            transactionPort.executeInTransaction {
                orderRepository.deleteOrder(order)
                storageEventRepository.save(OrderDeleted(order.hostName, order.hostOrderId))
            } ?: throw RuntimeException("Could not delete order")

        processStorageEventAsync(storageEvent)
    }

    override suspend fun updateOrder(
        hostName: HostName,
        hostOrderId: String,
        itemHostIds: List<String>,
        orderType: Order.Type,
        contactPerson: String,
        address: Order.Address?,
        note: String?,
        callbackUrl: String
    ): Order {
        val itemIds = itemHostIds.map { ItemId(hostName, it) }

        if (!itemRepository.doesEveryItemExist(itemIds)) {
            throw ValidationException("All order items in order must exist")
        }

        val order = getOrderOrThrow(hostName, hostOrderId)

        val (updatedOrder, storageEvent) =
            transactionPort.executeInTransaction {
                val updatedOrder =
                    orderRepository.updateOrder(
                        order.updateOrder(
                            itemIds = itemHostIds,
                            callbackUrl = callbackUrl,
                            orderType = orderType,
                            address = address ?: order.address,
                            note = note,
                            contactPerson = contactPerson
                        )
                    )
                val storageEvent = storageEventRepository.save(OrderUpdated(updatedOrder))

                (updatedOrder to storageEvent)
            } ?: throw RuntimeException("Could not update order")

        processStorageEventAsync(storageEvent)

        return updatedOrder
    }

    override suspend fun updateOrderStatus(
        hostName: HostName,
        hostOrderId: String,
        status: Order.Status
    ): Order {
        val order =
            orderRepository.getOrder(hostName, hostOrderId)
                ?: throw OrderNotFoundException("No order with hostName: $hostName and hostOrderId: $hostOrderId exists")

        val (updatedOrder, catalogEvent) =
            transactionPort.executeInTransaction {
                val updatedOrder = orderRepository.updateOrder(order.copy(status = status))
                val catalogEvent = catalogEventRepository.save(OrderEvent(updatedOrder))

                (updatedOrder to catalogEvent)
            } ?: throw RuntimeException("Could not update order status")

        processCatalogEventAsync(catalogEvent)

        return updatedOrder
    }

    override suspend fun getItem(
        hostName: HostName,
        hostId: String
    ): Item? {
        return itemRepository.getItem(hostName, hostId)
    }

    override suspend fun getOrder(
        hostName: HostName,
        hostOrderId: String
    ): Order? {
        return orderRepository.getOrder(hostName, hostOrderId)
    }

    private suspend fun getItems(
        hostIds: List<String>,
        hostName: HostName
    ): List<Item> {
        return itemRepository.getItems(hostName, hostIds)
    }

    override suspend fun synchronizeItems(items: List<SynchronizeItems.ItemToSynchronize>) {
        val syncItemsById = items.associateBy { (it.hostId to it.hostName) }
        items
            .groupBy { it.hostName }
            .forEach { (hostName, syncItems) ->
                transactionPort.executeInTransaction {
                    val ids = syncItems.map { it.hostId }
                    val existingItems = getItems(ids, hostName)
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
    ): Order {
        return getOrder(
            hostName,
            hostOrderId
        ) ?: throw OrderNotFoundException("No order with hostOrderId: $hostOrderId and hostName: $hostName exists")
    }

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
}
