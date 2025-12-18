package no.nb.mlt.wls.domain.model.events.email

import no.nb.mlt.wls.domain.model.Order
import java.util.UUID

data class OrderCompleteMail(
    val order: Order,
    override val id: String = UUID.randomUUID().toString()
) : EmailEvent {
    override val body: Any
        get() = order
}
