package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order

fun interface EmailNotifier {
    suspend fun orderCreated(
        order: Order,
        orderItems: List<Item>
    )
}
