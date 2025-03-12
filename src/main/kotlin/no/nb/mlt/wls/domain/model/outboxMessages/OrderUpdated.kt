package no.nb.mlt.wls.domain.model.outboxMessages

import no.nb.mlt.wls.domain.model.Order
import java.util.UUID

data class OrderUpdated(
    val updatedOrder: Order,
    override val id: String = UUID.randomUUID().toString()
) : OutboxMessage {
    override val body: Any
        get() = updatedOrder
}
