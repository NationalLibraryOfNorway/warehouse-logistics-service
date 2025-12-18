package no.nb.mlt.wls.domain.model.events.email

import no.nb.mlt.wls.domain.model.Order
import java.util.*

data class OrderCancellationMail(
    val cancelledOrder: Order,
    override val id: String = UUID.randomUUID().toString()
) : EmailEvent {
    override val body: Any
        get() = cancelledOrder
}
