package no.nb.mlt.wls.infrastructure.email

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.UserNotifier

private val logger = KotlinLogging.logger {}

class DisabledEmailNotifier : UserNotifier {
    override suspend fun orderConfirmation(order: Order): Boolean {
        logger.warn { "Sending emails for orders is disabled" }
        return true
    }

    override suspend fun orderPickup(
        order: Order,
        items: List<Item>
    ): Boolean {
        logger.warn { "Sending emails for orders is disabled" }
        return true
    }

    override suspend fun orderCompleted(order: Order): Boolean {
        logger.warn { "Sending emails for orders is disabled" }
        return true
    }
}
