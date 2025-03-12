package no.nb.mlt.wls.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.outboxMessages.ItemCreated
import no.nb.mlt.wls.domain.model.outboxMessages.OrderCreated
import no.nb.mlt.wls.domain.model.outboxMessages.OrderDeleted
import no.nb.mlt.wls.domain.model.outboxMessages.OrderUpdated
import no.nb.mlt.wls.domain.model.outboxMessages.OutboxMessage
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
import no.nb.mlt.wls.domain.ports.outbound.InventoryNotifier
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository.ItemId
import no.nb.mlt.wls.domain.ports.outbound.OrderRepository
import no.nb.mlt.wls.domain.ports.outbound.OutboxMessageProcessor
import no.nb.mlt.wls.domain.ports.outbound.OutboxRepository
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemException
import no.nb.mlt.wls.domain.ports.outbound.TransactionPort

private val logger = KotlinLogging.logger {}

class WLSService(
    private val itemRepository: ItemRepository,
    private val orderRepository: OrderRepository,
    private val inventoryNotifier: InventoryNotifier,
    private val outboxRepository: OutboxRepository,
    private val transactionPort: TransactionPort,
    private val outboxMessageProcessor: OutboxMessageProcessor
) : AddNewItem, CreateOrder, DeleteOrder, UpdateOrder, GetOrder, GetItem, OrderStatusUpdate, MoveItem, PickOrderItems, PickItems, SynchronizeItems {
    private val coroutineContext = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun addItem(itemMetadata: ItemMetadata): Item {
        getItem(itemMetadata.hostName, itemMetadata.hostId)?.let {
            logger.info { "Item already exists: $it" }
            return it
        }

        val (createdItem, outboxMessage) =
            transactionPort.executeInTransaction {
                val createdItem = itemRepository.createItem(itemMetadata.toItem())
                val message = outboxRepository.save(ItemCreated(createdItem))

                (createdItem to message)
            } ?: throw RuntimeException("Could not create item")

        processMessageAsync(outboxMessage)
        return createdItem
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
            val pickedItemsQuantity = itemsPickedMap[item.hostId] ?: 0
            val pickedItem = item.pickItem(pickedItemsQuantity)

            // Picking an item is guaranteed to set quantity or location.
            // An exception is thrown otherwise
            val movedItem =
                itemRepository.moveItem(
                    item.hostId,
                    item.hostName,
                    pickedItem.quantity,
                    pickedItem.location
                )

            inventoryNotifier.itemChanged(movedItem)
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
        val pickedOrder = orderRepository.updateOrder(order.pickOrder(pickedHostIds))
        inventoryNotifier.orderChanged(pickedOrder)
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

        val (order, orderCreatedMessage) =
            transactionPort.executeInTransaction {
                val order = orderRepository.createOrder(orderDTO.toOrder())
                val orderCreatedMessage = outboxRepository.save(OrderCreated(createdOrder = order))

                (order to orderCreatedMessage)
            } ?: throw RuntimeException("Could not create order")

        processMessageAsync(orderCreatedMessage)

        return order
    }

    override suspend fun deleteOrder(
        hostName: HostName,
        hostOrderId: String
    ) {
        val order = getOrderOrThrow(hostName, hostOrderId).deleteOrder()
        val outBoxMessage =
            transactionPort.executeInTransaction {
                orderRepository.deleteOrder(order)
                outboxRepository.save(OrderDeleted(order.hostName, order.hostOrderId))
            } ?: throw RuntimeException("Could not delete order")

        outboxMessageProcessor.handleEvent(outBoxMessage)
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

        val (updatedOrder, outboxMessage) =
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
                val message = outboxRepository.save(OrderUpdated(updatedOrder = updatedOrder))
                (updatedOrder to message)
            } ?: throw RuntimeException("Could not update order")

        processMessageAsync(outboxMessage)

        return updatedOrder
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

    private fun processMessageAsync(outboxMessage: OutboxMessage) =
        coroutineContext.launch {
            try {
                outboxMessageProcessor.handleEvent(outboxMessage)
            } catch (e: StorageSystemException) {
                logger.error(e) {
                    "Storage system reported error while processing outbox message: $outboxMessage. Try again later"
                }
            } catch (e: DuplicateResourceException) {
                // TODO - What to do in this case? Should we try to recover?
                logger.warn(e) {
                    "Outbox message produced a duplicate resource. Message: $outboxMessage"
                }
            } catch (e: Exception) {
                logger.error(e) {
                    "Unexpected error while processing outbox message: $outboxMessage. Try again later"
                }
            }
        }

    override fun synchronizeItems(items: List<SynchronizeItems.ItemToSynchronize>) {
        // TODO: Implement synchronization (reconciliation) of items from storage systems.
        logger.warn { "Synchronizing items not implemented" }
    }
}
