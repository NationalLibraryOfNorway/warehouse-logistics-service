package no.nb.mlt.wls.infrastructure.repository

import no.nb.mlt.wls.domain.HostName
import no.nb.mlt.wls.domain.Item
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Component
class ItemRepositoryMongoAdapter(
    private val mongoRepo: ItemMongoRepository
) : ItemRepository {
    override fun getItem(
        hostName: HostName,
        hostId: String
    ): Mono<Item> {
        return mongoRepo.findByHostNameAndHostId(hostName, hostId)
    }

    override fun createItem(item: Item): Mono<Item> {
        return mongoRepo.save(item)
    }
}

@Repository
interface ItemMongoRepository : ReactiveMongoRepository<Item, String> {
    fun findByHostNameAndHostId(
        hostName: HostName,
        hostId: String
    ): Mono<Item>
}
