package no.nb.mlt.wls.infrastructure.repositories.item

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository.ItemId
import no.nb.mlt.wls.domain.ports.outbound.exceptions.ItemMetadataUpdateException
import no.nb.mlt.wls.domain.ports.outbound.exceptions.ItemMovingException
import no.nb.mlt.wls.infrastructure.config.TimeoutProperties
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.data.mongodb.repository.Update
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
class MongoItemRepositoryAdapter(
    private val mongoRepo: ItemMongoRepository,
    private val timeoutConfig: TimeoutProperties
) : ItemRepository {
    override suspend fun getItem(
        hostName: HostName,
        hostId: String
    ): Item? =
        mongoRepo
            .findByHostNameAndHostId(hostName, hostId)
            .map(MongoItem::toItem)
            .timeout(timeoutConfig.mongo)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while fetching item from WLS database. hostName: $hostName, hostId: $hostId"
                }
            }.awaitSingleOrNull()

    override suspend fun getItemsById(hostId: String): List<Item> =
        mongoRepo
            .findAllByHostId(hostId)
            .collectList()
            .timeout(timeoutConfig.mongo)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while fetching item from WLS database. hostId: $hostId"
                }
            }.awaitSingle()
            .map { it.toItem() }

    override suspend fun getItemsByIds(
        hostName: HostName,
        hostIds: List<String>
    ): List<Item> =
        mongoRepo
            .findAllByHostNameAndHostIdIn(hostName, hostIds)
            .collectList()
            .timeout(timeoutConfig.mongo)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while fetching multiple items for $hostName"
                }
            }.awaitSingle()
            .map { it.toItem() }

    override suspend fun getAllItemsForHosts(hostnames: List<HostName>): List<Item> =
        mongoRepo
            .findAllByHostNameIn(hostnames)
            .collectList()
            .timeout(timeoutConfig.mongo)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while fetching all items for $hostnames"
                }
            }.awaitSingle()
            .map { it.toItem() }

    override suspend fun createItem(item: Item): Item =
        mongoRepo
            .save(item.toMongoItem())
            .map(MongoItem::toItem)
            .timeout(timeoutConfig.mongo)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while saving to WLS database. item: $item"
                }
            }.awaitSingle()

    override suspend fun editItem(item: Item): Boolean {
        val itemsEdited =
            mongoRepo
                .findAndUpdateItemMetadataByHostNameAndHostId(
                    item.hostName,
                    item.hostId,
                    item.description,
                    item.itemCategory,
                    item.preferredEnvironment,
                    item.packaging,
                    item.callbackUrl
                ).timeout(timeoutConfig.mongo)
                .doOnError {
                    logger.error(it) {
                        if (it is TimeoutException) {
                            "Timed out while updating item metadata. Item: $item"
                        } else {
                            "Error while updating item metadata. Item: $item"
                        }
                    }
                }.onErrorMap { ItemMetadataUpdateException(it.message ?: "Item metadata could not be updated", it) }
                .awaitSingle()

        return itemsEdited != 0L
    }

    override suspend fun doesEveryItemExist(ids: List<ItemId>): Boolean =
        mongoRepo
            .countItemsMatchingIds(ids)
            .map {
                logger.debug { "Counted items matching ids: $ids, count: $it" }
                it == ids.size.toLong()
            }.timeout(timeoutConfig.mongo)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while counting items matching ids: $ids"
                }
            }.awaitSingle()

    override suspend fun moveItem(item: Item): Boolean {
        val hostName = item.hostName
        val hostId = item.hostId
        val quantity = item.quantity
        val location = item.location
        val associatedStorage = item.associatedStorage

        val itemsModified =
            mongoRepo
                .findAndUpdateItemByHostNameAndHostId(hostName, hostId, quantity, location, associatedStorage)
                .timeout(timeoutConfig.mongo)
                .doOnError {
                    logger.error(it) {
                        if (it is TimeoutException) {
                            "Timed out while updating Item. Host ID: $hostId, Host: $hostName"
                        } else {
                            "Error while updating item. Host ID: $hostId, Host: $hostName"
                        }
                    }
                }.onErrorMap { ItemMovingException(it.message ?: "Item could not be moved", it) }
                .awaitSingle()

        if (itemsModified != 0L) {
            logger.debug { "Item was moved. hostId=$hostId, hostName=$hostName, location=$location, quantity=$quantity" }
            return true
        }
        return false
    }
}

@Repository
interface ItemMongoRepository : ReactiveMongoRepository<MongoItem, String> {
    fun findByHostNameAndHostId(
        hostName: HostName,
        hostId: String
    ): Mono<MongoItem>

    fun findAllByHostId(hostId: String): Flux<MongoItem>

    @Query(count = true, value = $$"{ '$or': ?0 }")
    fun countItemsMatchingIds(ids: List<ItemId>): Mono<Long>

    @Query("{hostName: ?0,hostId: ?1}")
    @Update($$"{'$set':{quantity: ?2,location: ?3,associatedStorage: ?4}}")
    fun findAndUpdateItemByHostNameAndHostId(
        hostName: HostName,
        hostId: String,
        quantity: Int,
        location: String,
        associatedStorage: AssociatedStorage
    ): Mono<Long>

    @Query("{hostName: ?0,hostId: ?1}")
    @Update($$"{'$set':{description: ?2,itemCategory: ?3,preferredEnvironment: ?4,packaging: ?5,callbackUrl: ?6}}")
    fun findAndUpdateItemMetadataByHostNameAndHostId(
        hostName: HostName,
        hostId: String,
        description: String,
        itemCategory: ItemCategory,
        preferredEnvironment: Environment,
        packaging: Packaging,
        callbackUrl: String?
    ): Mono<Long>

    fun findAllByHostNameAndHostIdIn(
        hostName: HostName,
        hostId: Collection<String>
    ): Flux<MongoItem>

    fun findAllByHostNameIn(hostnames: Collection<HostName>): Flux<MongoItem>
}
