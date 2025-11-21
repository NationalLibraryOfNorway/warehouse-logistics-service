package no.nb.mlt.wls.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.events.email.EmailEvent
import no.nb.mlt.wls.domain.model.events.email.OrderConfirmationMail
import no.nb.mlt.wls.domain.model.events.email.OrderPickupMail
import no.nb.mlt.wls.domain.ports.outbound.UserNotifier
import no.nb.mlt.wls.domain.ports.outbound.EventProcessor
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class EmailEventProcessorAdapter(
    val emailEventRepository: EventRepository<EmailEvent>,
    val userNotifier: UserNotifier
) : EventProcessor<EmailEvent> {
    override suspend fun processOutbox() {
        logger.trace { "Processing email event outbox" }

        val outboxMessages = emailEventRepository.getUnprocessedSortedByCreatedTime()

        if (outboxMessages.isEmpty()) {
            logger.debug { "No emails in outbox" }
            return
        }

        logger.trace { "Processing ${outboxMessages.size} emails in outbox" }
        outboxMessages.forEach { event ->
            handleEvent(event)
        }
    }

    override suspend fun handleEvent(event: EmailEvent) {
        val isSuccessful =
            when (event) {
                is OrderConfirmationMail -> userNotifier.orderConfirmation(event.order)
                is OrderPickupMail -> userNotifier.orderPickup(event.orderEmail)
            }
        if (isSuccessful) {
            emailEventRepository.markAsProcessed(event)
        }
    }
}
