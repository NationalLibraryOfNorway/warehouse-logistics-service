package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import java.time.Instant

interface InventoryNotifier {
    fun itemChanged(
        item: Item,
        eventTimestamp: Instant,
        messageId: String
    )

    fun orderChanged(
        order: Order,
        eventTimestamp: Instant,
        messageId: String
    )
}
