package no.nb.mlt.wls.domain.model.catalogEvents

import no.nb.mlt.wls.domain.model.Item
import java.time.Instant
import java.util.UUID

data class ItemEvent(
    val item: Item,
    override val id: String = UUID.randomUUID().toString(),
    override val eventTimestamp: Instant = Instant.now(),
) : CatalogEvent {
    override val body: Any
        get() = item
}
