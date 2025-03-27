package no.nb.mlt.wls.domain.model.events.storage

import no.nb.mlt.wls.domain.model.Item
import java.util.UUID

data class ItemCreated(
    val createdItem: Item,
    override val id: String = UUID.randomUUID().toString()
) : StorageEvent {
    override val body: Any
        get() = createdItem
}
