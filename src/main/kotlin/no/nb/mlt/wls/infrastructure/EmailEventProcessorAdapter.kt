package no.nb.mlt.wls.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.events.email.EmailEvent
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.domain.ports.outbound.EventProcessor
import no.nb.mlt.wls.domain.ports.outbound.EventRepository

private val logger = KotlinLogging.logger {}

class EmailEventProcessorAdapter(
    val emailEventRepository: EventRepository<EmailEvent>,
    val emailNotifier: EmailNotifier
): EventProcessor<EmailEvent> {
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
        TODO("Implement handling order confirmations")
        TODO("Implement handling order handler emails")
    }
}
