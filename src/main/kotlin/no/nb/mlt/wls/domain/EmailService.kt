package no.nb.mlt.wls.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.events.email.EmailEvent
import no.nb.mlt.wls.domain.model.events.email.OrderConfirmationMail
import no.nb.mlt.wls.domain.model.events.email.OrderHandlerMail
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class EmailService(
    private val emailEventRepository: EventRepository<EmailEvent>
) {
    suspend fun createOrderConfirmation(order: Order) {
        if (order.contactEmail != null) {
            emailEventRepository.save(OrderConfirmationMail(order))
        } else {
            logger.warn { "No order email available for ${order.contactPerson} in order ${order.hostOrderId}" }
        }
    }

    suspend fun createOrderHandlerEmail(order: Order, orderItems: List<Item>) {
        emailEventRepository.save(OrderHandlerMail(order, orderItems))
    }
}
