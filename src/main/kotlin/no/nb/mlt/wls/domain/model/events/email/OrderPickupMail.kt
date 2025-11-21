package no.nb.mlt.wls.domain.model.events.email

import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import java.util.UUID

data class OrderPickupMail(
    val order: Order,
    val orderItems: List<Item>,
    override val id: String = UUID.randomUUID().toString()
) : EmailEvent {
    override val body: Any
        get() = (order to orderItems)
}
