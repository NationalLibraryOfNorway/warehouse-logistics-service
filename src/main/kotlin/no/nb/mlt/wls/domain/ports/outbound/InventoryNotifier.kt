package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order

interface InventoryNotifier {
    fun itemChanged(item: Item)

    fun orderChanged(order: Order)
}
