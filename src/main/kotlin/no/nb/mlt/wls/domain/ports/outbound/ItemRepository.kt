package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import reactor.core.publisher.Mono

interface ItemRepository {
    fun getItem(
        hostName: HostName,
        hostId: String
    ): Mono<Item>

    fun createItem(item: Item): Mono<Item>

    suspend fun doesAllItemsExist(ids: List<ItemId>): Boolean
}

data class ItemId(val hostName: HostName, val hostId: String)
