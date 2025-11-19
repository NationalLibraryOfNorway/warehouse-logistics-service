package no.nb.mlt.wls.infrastructure.email

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier

private val logger = KotlinLogging.logger {}

class DisabledEmailAdapter : EmailNotifier {
    override suspend fun sendOrderConfirmation(order: Order) {
        logger.warn { "Sending emails for orders is disabled" }
    }

    override suspend fun sendOrderHandlerMail(
        order: Order,
        items: List<Item>
    ) {
        logger.warn { "Sending emails for orders is disabled" }
    }

    override suspend fun orderCompleted(order: Order) {
        logger.warn { "Sending emails for orders is disabled" }
    }
}
