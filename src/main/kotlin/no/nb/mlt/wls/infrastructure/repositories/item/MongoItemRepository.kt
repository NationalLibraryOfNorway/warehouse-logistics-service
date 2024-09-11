package no.nb.mlt.wls.infrastructure.repositories.item

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
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
        return mongoRepo
            .findByHostNameAndHostId(hostName, hostId)
            .map(MongoItem::toItem)
    }

    override fun createItem(item: Item): Mono<Item> {
        return mongoRepo.save(item.toMongoItem()).map(MongoItem::toItem)
    }
}

@Repository
interface ItemMongoRepository : ReactiveMongoRepository<MongoItem, String> {
    fun findByHostNameAndHostId(
        hostName: HostName,
        hostId: String
    ): Mono<MongoItem>
}


