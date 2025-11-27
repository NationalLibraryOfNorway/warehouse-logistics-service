package no.nb.mlt.wls.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.events.email.EmailEvent
import no.nb.mlt.wls.domain.model.events.email.OrderConfirmationMail
import no.nb.mlt.wls.domain.model.events.email.OrderPickupMail
import no.nb.mlt.wls.domain.model.events.email.createOrderPickupData
import no.nb.mlt.wls.domain.ports.outbound.EventProcessor
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.TransactionPort
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class EmailService(
    private val emailEventRepository: EventRepository<EmailEvent>,
    private val emailEventProcessor: EventProcessor<EmailEvent>,
    private val transactionPort: TransactionPort
) {
    suspend fun createOrderConfirmation(order: Order) {
        if (order.contactEmail == null) {
            logger.warn { "No order email available for ${order.contactPerson} in order ${order.hostOrderId}" }
            return
        }
        val event = transactionPort.executeInTransaction {
            emailEventRepository.save(OrderConfirmationMail(order))
        }
        emailEventProcessor.handleEvent(event)
    }

    suspend fun createOrderPickup(order: Order, orderItems: List<Item>) {
        val event = transactionPort.executeInTransaction {
            emailEventRepository.save(OrderPickupMail(createOrderPickupData(order, orderItems)))
        }
        emailEventProcessor.handleEvent(event)
    }
}
