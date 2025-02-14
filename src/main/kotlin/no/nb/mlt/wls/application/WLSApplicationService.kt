package no.nb.mlt.wls.application

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.CreateOrder
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import no.nb.mlt.wls.domain.ports.inbound.GetOrder
import no.nb.mlt.wls.domain.ports.outbound.TransactionPort
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class WLSApplicationService(
    private val transactionPort: TransactionPort,
    private val createOrder: CreateOrder,
    private val getOrder: GetOrder
) {
    suspend fun createOrder(order: CreateOrderDTO): OrderCreated {
        getOrder.getOrder(order.hostName, order.hostOrderId)?.let {
            logger.info { "Order already exists: $it" }
            return OrderCreated(order = it, isNew = false)
        }

        logger.info { "Order did not exists. Creating order: $order" }

        return transactionPort.executeInTransaction {
            val createdOrder = createOrder.createOrder(order)
            logger.info { "Created order: $createdOrder" }
            OrderCreated(order = createdOrder, isNew = true)
        } ?: throw Exception("Failed to create order")
    }
}

data class OrderCreated(val order: Order, val isNew: Boolean)
