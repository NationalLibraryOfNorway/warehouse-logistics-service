package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order

interface CallbackHandler {

    fun handleItemCallback(item: Item)

    fun handleOrderCallback(order: Order)
}
