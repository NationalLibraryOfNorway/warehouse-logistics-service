package no.nb.mlt.wls.infrastructure.repositories.item

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.ports.outbound.ItemId
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
class ItemRepositoryMongoAdapter(
    private val mongoRepo: ItemMongoRepository
) : ItemRepository {
    override suspend fun getItem(
        hostName: HostName,
        hostId: String
    ): Item? {
        return mongoRepo
            .findByHostNameAndHostId(hostName, hostId)
            .map(MongoItem::toItem)
            .timeout(Duration.ofSeconds(8))
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while fetching from WLS database. hostName: $hostName, hostId: $hostId"
                }
            }
            .awaitSingleOrNull()
    }

    override fun createItem(item: Item): Mono<Item> {
        return mongoRepo.save(item.toMongoItem()).map(MongoItem::toItem)
    }

    override suspend fun doesAllItemsExist(ids: List<ItemId>): Boolean {
        return mongoRepo.countItemsMatchingIds(ids)
            .map {
                logger.debug { "Counted items matching ids: $ids, count: $it" }
                it == ids.size.toLong()
            }
            .timeout(Duration.ofSeconds(8))
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while counting items matching ids: $ids"
                }
            }
            .awaitSingle()
    }
}

@Repository
interface ItemMongoRepository : ReactiveMongoRepository<MongoItem, String> {
    fun findByHostNameAndHostId(
        hostName: HostName,
        hostId: String
    ): Mono<MongoItem>

    @Query(count = true, value = "{ '\$or': ?0 }")
    fun countItemsMatchingIds(ids: List<ItemId>): Mono<Long>
}
