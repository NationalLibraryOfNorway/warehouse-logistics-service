package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order

interface EmailNotifier {
    suspend fun orderCreated(
        order: Order,
        orderItems: List<Item>
    )

    suspend fun orderUpdated(order: Order)
}
