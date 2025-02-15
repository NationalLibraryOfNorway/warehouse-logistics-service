package no.nb.mlt.wls.domain.model.outboxMessages

import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.OutboxMessage

data class OrderUpdated(
    val updatedOrder: Order
) : OutboxMessage {
    override val body: Any
        get() = updatedOrder
}
