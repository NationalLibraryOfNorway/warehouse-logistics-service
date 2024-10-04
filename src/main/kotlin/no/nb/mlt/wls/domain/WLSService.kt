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
import no.nb.mlt.wls.domain.ports.inbound.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.ServerException
import no.nb.mlt.wls.domain.ports.inbound.UpdateOrder
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import no.nb.mlt.wls.domain.ports.inbound.toItem
import no.nb.mlt.wls.domain.ports.inbound.toOrder
import no.nb.mlt.wls.domain.ports.outbound.DuplicateResourceException
import no.nb.mlt.wls.domain.ports.outbound.ItemId
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.OrderRepository
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import java.time.Duration
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

class WLSService(
    private val itemRepository: ItemRepository,
    private val orderRepository: OrderRepository,
    private val storageSystemFacade: StorageSystemFacade
) : AddNewItem, CreateOrder, DeleteOrder, UpdateOrder, GetOrder, GetItem, MoveItem {
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

    override suspend fun moveItem(
        hostId: String,
        hostName: HostName,
        quantity: Double,
        location: String
    ): Item {
        if (quantity < 0.0) {
            throw ValidationException("Quantity can not be negative")
        }
        if (location.isBlank()) {
            throw ValidationException("Location can not be blank")
        }
        val item = getItem(hostName, hostId) ?: throw ItemNotFoundException("Item with id '$hostId' does not exist for '$hostName'")
        return itemRepository.moveItem(item.hostId, item.hostName, quantity, location)
    }

    override suspend fun createOrder(orderDTO: CreateOrderDTO): Order {
        orderRepository.getOrder(orderDTO.hostName, orderDTO.hostOrderId)?.let {
            logger.info { "Order already exists: $it" }
            return it
        }

        val itemIds = orderDTO.orderItems.map { ItemId(orderDTO.hostName, it.hostId) }
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
        storageSystemFacade.deleteOrder(hostName, hostOrderId)
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

        val order =
            getOrder(
                hostName,
                hostOrderId
            ) ?: throw OrderNotFoundException("No order with hostOrderId: $hostOrderId and hostName: $hostName exists")

        val updatedOrder =
            order
                .setProductLines(itemHostIds)
                .setCallbackUrl(callbackUrl)
                .setOrderType(orderType)
                .setReceiver(receiver)

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
}
