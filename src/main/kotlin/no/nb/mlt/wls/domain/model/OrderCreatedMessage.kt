package no.nb.mlt.wls.domain.model

import no.nb.mlt.wls.domain.ports.outbound.OutboxMessage

data class OrderCreatedMessage(
    val order: Order
): OutboxMessage
