package no.nb.mlt.wls.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.AddNewItem
import no.nb.mlt.wls.domain.ports.inbound.CreateOrder
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import no.nb.mlt.wls.domain.ports.inbound.DeleteOrder
import no.nb.mlt.wls.domain.ports.inbound.GetOrder
import no.nb.mlt.wls.domain.ports.inbound.ItemMetadata
import no.nb.mlt.wls.domain.ports.inbound.UpdateOrder
import no.nb.mlt.wls.domain.ports.inbound.toItem
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
) : AddNewItem, CreateOrder, DeleteOrder, UpdateOrder, GetOrder {
    override suspend fun addItem(itemMetadata: ItemMetadata): Item {
        val existingItem =
            itemRepository.getItem(itemMetadata.hostName, itemMetadata.hostId)
                // TODO - See if timeouts can be made configurable
                .timeout(Duration.ofSeconds(8))
                .doOnError(TimeoutException::class.java) {
                    logger.error(it) {
                        "Timed out while fetching from WLS database. item: $itemMetadata"
                    }
                }
                .awaitSingleOrNull()

        if (existingItem != null) {
            logger.info { "Item already exists: $existingItem" }
            return existingItem
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

    override suspend fun createOrder(orderDTO: CreateOrderDTO): Order {
        TODO("Not refactored yet")
    }

    override suspend fun deleteOrder(hostName: HostName, hostOrderId: String) {
        TODO("Not refactored yet")
    }

    override suspend fun updateOrder(
        hostName: HostName,
        hostOrderId: String,
        orderItems: List<Order.OrderItem>,
        orderType: Order.Type,
        receiver: Order.Receiver,
        callbackUrl: String
    ): Order {
        TODO("Not refactored yet")
    }

    override suspend fun getOrder(hostName: HostName, hostOrderId: String): Order {
        TODO("Not refactored yet")
    }
}
