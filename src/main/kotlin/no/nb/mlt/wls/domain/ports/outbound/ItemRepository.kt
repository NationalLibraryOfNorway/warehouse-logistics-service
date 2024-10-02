package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import reactor.core.publisher.Mono

interface ItemRepository {
    suspend fun getItem(
        hostName: HostName,
        hostId: String
    ): Item?

    fun createItem(item: Item): Mono<Item>

    suspend fun doesAllItemsExist(ids: List<ItemId>): Boolean

    fun moveItem(
        hostId: String,
        hostName: HostName,
        quantity: Double,
        location: String
    ): Mono<Item>
}

data class ItemId(val hostName: HostName, val hostId: String)
