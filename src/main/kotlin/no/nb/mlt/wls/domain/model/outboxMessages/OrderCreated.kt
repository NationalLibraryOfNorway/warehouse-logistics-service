package no.nb.mlt.wls.domain.model.outboxMessages

import no.nb.mlt.wls.domain.model.Order

data class OrderCreated(
    val createdOrder: Order
) : OutboxMessage {
    override val body: Any
        get() = createdOrder
}
