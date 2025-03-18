package no.nb.mlt.wls.domain.model.catalogMessages

import no.nb.mlt.wls.domain.model.Order
import java.time.Instant
import java.util.UUID

data class OrderUpdate(
    val order: Order,
    override val id: String = UUID.randomUUID().toString(),
    override val messageTimestamp: Instant
) : CatalogMessage {
    override val body: Any
        get() = order
}
