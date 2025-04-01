package no.nb.mlt.wls.infrastructure.repositories.item

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.ports.inbound.ItemNotFoundException
import no.nb.mlt.wls.domain.ports.outbound.ItemMovingException
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository.ItemId
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.data.mongodb.repository.Update
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
class MongoItemRepositoryAdapter(
    private val mongoRepo: ItemMongoRepository
) : ItemRepository {
    override suspend fun getItem(
        hostName: HostName,
        hostId: String
    ): Item? =
        mongoRepo
            .findByHostNameAndHostId(hostName, hostId)
            .map(MongoItem::toItem)
            .timeout(Duration.ofSeconds(8))
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while fetching from WLS database. hostName: $hostName, hostId: $hostId"
                }
            }.awaitSingleOrNull()

    override suspend fun getItems(
        hostName: HostName,
        hostIds: List<String>
    ): List<Item> =
        mongoRepo
            .findAllByHostNameAndHostIdIn(hostName, hostIds)
            .collectList()
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while fetching multiple items for $hostName"
                }
            }.awaitSingle()
            .map { it.toItem() }

    override suspend fun createItem(item: Item): Item =
        mongoRepo
            .save(item.toMongoItem())
            .map(MongoItem::toItem)
            .timeout(Duration.ofSeconds(6))
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while saving to WLS database. item: $item"
                }
            }.awaitSingle()

    override suspend fun doesEveryItemExist(ids: List<ItemId>): Boolean =
        mongoRepo
            .countItemsMatchingIds(ids)
            .map {
                logger.debug { "Counted items matching ids: $ids, count: $it" }
                it == ids.size.toLong()
            }.timeout(Duration.ofSeconds(8))
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while counting items matching ids: $ids"
                }
            }.awaitSingle()

    override suspend fun moveItem(
        hostName: HostName,
        hostId: String,
        quantity: Int,
        location: String
    ): Item {
        val itemsModified =
            mongoRepo
                .findAndUpdateItemByHostNameAndHostId(hostName, hostId, quantity, location)
                .timeout(Duration.ofSeconds(8))
                .doOnError {
                    logger.error(it) {
                        if (it is TimeoutException) {
                            "Timed out while updating Item. Host ID: $hostId, Host: $hostName"
                        } else {
                            "Error while updating item"
                        }
                    }
                }.onErrorMap { ItemMovingException(it.message ?: "Item could not be moved", it) }
                .awaitSingle()

        if (itemsModified == 0L) {
            throw ItemNotFoundException("Item was not found. Host ID: $hostId, Host: $hostName")
        }

        return getItem(hostName, hostId)!!
    }

    override suspend fun updateLocationAndQuantity(
        hostId: String,
        hostName: HostName,
        location: String,
        quantity: Int
    ): Item {
        val itemsModified =
            mongoRepo
                .findAndUpdateItemByHostNameAndHostId(hostName, hostId, quantity, location)
                .timeout(Duration.ofSeconds(8))
                .doOnError {
                    logger.error(it) {
                        if (it is TimeoutException) {
                            "Timed out while updating Item. Host ID: $hostId, Host: $hostName"
                        } else {
                            "Error while updating item"
                        }
                    }
                }.onErrorMap { ItemMovingException(it.message ?: "Item could not be updated", it) }
                .awaitSingle()

        if (itemsModified == 0L) {
            logger.warn { "Item was not updated. hostId=$hostId, hostName=$hostName, location=$location, quantity=$quantity" }
        } else {
            logger.debug { "Item was updated. hostId=$hostId, hostName=$hostName, location=$location, quantity=$quantity" }
        }

        return getItem(hostName, hostId)!!
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

    @Query("{hostName: ?0,hostId: ?1}")
    @Update("{'\$set':{quantity: ?2,location: ?3}}")
    fun findAndUpdateItemByHostNameAndHostId(
        hostName: HostName,
        hostId: String,
        quantity: Int,
        location: String
    ): Mono<Long>

    fun findAllByHostNameAndHostIdIn(
        hostName: HostName,
        hostId: Collection<String>
    ): Flux<MongoItem>
}
