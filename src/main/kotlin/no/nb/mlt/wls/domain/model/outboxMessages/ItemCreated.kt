package no.nb.mlt.wls.domain.model.outboxMessages

import no.nb.mlt.wls.domain.model.Item

data class ItemCreated(
    val createdItem: Item
) : OutboxMessage {
    override val body: Any
        get() = createdItem
}
