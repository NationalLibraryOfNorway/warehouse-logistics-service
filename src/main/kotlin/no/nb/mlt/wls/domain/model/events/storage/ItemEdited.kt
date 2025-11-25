package no.nb.mlt.wls.domain.model.events.storage

import java.util.*

data class ItemEdited(
    val editInfo: EditedItemInfo,
    override val id: String = UUID.randomUUID().toString()
) : StorageEvent {
    override val body: Any
        get() = editInfo
}
