package no.nb.mlt.wls.infrastructure.email

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.events.email.OrderPickupMail
import no.nb.mlt.wls.domain.ports.outbound.UserNotifier

private val logger = KotlinLogging.logger {}

const val MESSAGE = "Sending emails for orders is disabled"

class DisabledEmailNotifier : UserNotifier {
    override suspend fun orderConfirmation(order: Order) = logOrderDisabled()

    override suspend fun orderPickup(orderPickupData: OrderPickupMail.OrderPickupData) = logOrderDisabled()

    override suspend fun orderCompleted(order: Order) = logOrderDisabled()

    override suspend fun orderCancelled(order: Order): Boolean = logOrderDisabled()

    private fun logOrderDisabled(): Boolean {
        logger.warn { MESSAGE }
        return true
    }
}
