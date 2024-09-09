package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.domain.Item
import reactor.core.publisher.Mono

interface ItemRepository {
    fun getItem(
        hostName: HostName,
        hostId: String
    ): Mono<Item>

    fun createItem(item: Item): Mono<Item>
}
