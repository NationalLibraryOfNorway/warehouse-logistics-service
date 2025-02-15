package no.nb.mlt.wls.application

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.AddNewItem
import no.nb.mlt.wls.domain.ports.inbound.CreateOrder
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import no.nb.mlt.wls.domain.ports.inbound.GetItem
import no.nb.mlt.wls.domain.ports.inbound.GetOrder
import no.nb.mlt.wls.domain.ports.inbound.ItemMetadata
import no.nb.mlt.wls.domain.ports.inbound.UpdateOrder
import no.nb.mlt.wls.domain.ports.outbound.TransactionPort
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class WLSApplicationService(
    private val transactionPort: TransactionPort,
    private val createOrder: CreateOrder,
    private val getOrder: GetOrder,
    private val updateOrder: UpdateOrder,
    private val addNewItem: AddNewItem,
    private val getItem: GetItem
) {
    suspend fun createOrder(order: CreateOrderDTO): OrderCreated {
        getOrder.getOrder(order.hostName, order.hostOrderId)?.let {
            logger.debug { "Order already exists: $it" }
            return OrderCreated(order = it, isNew = false)
        }

        logger.debug { "Order did not exists. Creating order: $order" }

        return transactionPort.executeInTransaction {
            val createdOrder = createOrder.createOrder(order)
            logger.debug { "Created order: $createdOrder" }
            OrderCreated(order = createdOrder, isNew = true)
        } ?: throw Exception("Failed to create order")
    }

    suspend fun updateOrder(
        hostName: HostName,
        hostOrderId: String,
        itemHostIds: List<String>,
        orderType: Order.Type,
        contactPerson: String,
        callbackUrl: String,
        address: Order.Address? = null,
        note: String? = null
    ): Order {
        return transactionPort.executeInTransaction {
            logger.debug { "Updating order" }
            updateOrder.updateOrder(
                hostName,
                hostOrderId,
                itemHostIds,
                orderType,
                contactPerson,
                address,
                note,
                callbackUrl
            )
        } ?: throw RuntimeException("Failed to update order")
    }

    suspend fun createItem(itemMetadata: ItemMetadata): ItemCreated {
        return transactionPort.executeInTransaction {
            getItem.getItem(itemMetadata.hostName, itemMetadata.hostId)?.let {
                return@executeInTransaction ItemCreated(item = it, isNew = false)
            }

            ItemCreated(item = addNewItem.addItem(itemMetadata), isNew = true)
        } ?: throw RuntimeException("Failed to create item")
    }
}

data class OrderCreated(val order: Order, val isNew: Boolean)

data class ItemCreated(val item: Item, val isNew: Boolean)
