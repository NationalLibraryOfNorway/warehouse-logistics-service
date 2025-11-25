package no.nb.mlt.wls.domain.model.events.storage

import no.nb.mlt.wls.domain.model.Item

data class EditedItemInfo(
    val editedItem: Item,
    val oldItem: Item
)
