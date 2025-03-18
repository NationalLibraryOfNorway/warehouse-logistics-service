package no.nb.mlt.wls.domain.model.catalogMessages

import no.nb.mlt.wls.domain.model.Item
import java.time.Instant
import java.util.UUID

data class ItemUpdate(
    val item: Item,
    override val id: String = UUID.randomUUID().toString(),
    override val messageTimestamp: Instant
) : CatalogMessage {
    override val body: Any
        get() = item
}
