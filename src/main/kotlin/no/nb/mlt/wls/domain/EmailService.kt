package no.nb.mlt.wls.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.events.email.EmailEvent
import no.nb.mlt.wls.domain.model.events.email.OrderCancellationMail
import no.nb.mlt.wls.domain.model.events.email.OrderCompleteMail
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
    private val coroutineContext = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun createOrderConfirmation(order: Order) {
        if (order.contactEmail == null) {
            logger.warn { "No contact email provided for ${order.contactPerson} in order ${order.hostOrderId}." }
            return
        }
        val event =
            transactionPort.executeInTransaction {
                emailEventRepository.save(OrderConfirmationMail(order))
            }
        processEmailEventAsync(event)
    }

    suspend fun createOrderPickup(
        order: Order,
        orderItems: List<Item>
    ) {
        val event =
            transactionPort.executeInTransaction {
                emailEventRepository.save(OrderPickupMail(createOrderPickupData(order, orderItems)))
            }
        processEmailEventAsync(event)
    }

    suspend fun createOrderCancellation(order: Order) {
        val event = emailEventRepository.save(OrderCancellationMail(order))

        processEmailEventAsync(event)
    }

    suspend fun createOrderCompletion(updatedOrder: Order) {
        val event = emailEventRepository.save(OrderCompleteMail(updatedOrder))

        processEmailEventAsync(event)
    }

    private suspend fun processEmailEventAsync(event: EmailEvent) {
        coroutineContext.launch {
            try {
                emailEventProcessor.handleEvent(event)
            } catch (e: Exception) {
                logger.error(e) {
                    "Error processing email event $event"
                }
            }
        }
    }
}
