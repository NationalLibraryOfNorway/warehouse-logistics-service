package no.nb.mlt.wls.domain.model.events.storage

import no.nb.mlt.wls.domain.model.Order
import java.util.*

data class OrderUpdated(
    val updatedOrder: Order,
    override val id: String = UUID.randomUUID().toString()
) : StorageEvent {
    override val body: Any
        get() = updatedOrder
}
