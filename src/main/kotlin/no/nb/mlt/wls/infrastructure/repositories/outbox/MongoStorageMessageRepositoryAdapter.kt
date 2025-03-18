package no.nb.mlt.wls.infrastructure.repositories.outbox

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.domain.model.storageMessages.StorageMessage
import no.nb.mlt.wls.domain.ports.outbound.StorageMessageRepository
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
class MongoStorageMessageRepositoryAdapter(
    private val mongoStorageMessageRepository: MongoStorageMessageRepository
) : StorageMessageRepository {
    override suspend fun save(storageMessage: StorageMessage): StorageMessage {
        val mongoMessage = MongoStorageMessage(body = storageMessage)
        return mongoStorageMessageRepository
            .save(mongoMessage)
            .map { it.body }
            .doOnEach { logger.info { "Saved outbox message: $it" } }
            .timeout(Duration.ofSeconds(8))
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while saving to outbox. Message: $storageMessage"
                    } else {
                        "Error while saving to outbox"
                    }
                }
            }
            .onErrorMap { StorageMessageRepository.RepositoryException("Could not save to outbox", it) }
            .awaitSingle()
    }

    override suspend fun getAll(): List<StorageMessage> {
        return mongoStorageMessageRepository.findAll()
            .map { it.body }
            .collectList()
            .timeout(Duration.ofSeconds(8))
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while fetching from outbox"
                    } else {
                        "Error while fetching from outbox"
                    }
                }
            }
            .onErrorMap { StorageMessageRepository.RepositoryException("Could not fetch from outbox", it) }
            .awaitSingle()
    }

    override suspend fun getUnprocessedSortedByCreatedTime(): List<StorageMessage> {
        return mongoStorageMessageRepository
            .findAllByProcessedTimestampIsNull()
            .map { it.body }
            .collectList()
            .timeout(Duration.ofSeconds(8))
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while fetching unprocessed from outbox"
                    } else {
                        "Error while fetching unprocessed from outbox"
                    }
                }
            }
            .onErrorMap { StorageMessageRepository.RepositoryException("Could not fetch unprocessed from outbox", it) }
            .awaitSingle()
    }

    override suspend fun markAsProcessed(storageMessage: StorageMessage): StorageMessage {
        return mongoStorageMessageRepository
            .save(MongoStorageMessage(body = storageMessage, processedTimestamp = java.time.Instant.now()))
            .map { it.body }
            .timeout(Duration.ofSeconds(8))
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while marking as processed in outbox. Message: $storageMessage"
                    } else {
                        "Error while marking as processed in outbox"
                    }
                }
            }
            .onErrorMap { StorageMessageRepository.RepositoryException("Could not mark as processed in outbox", it) }
            .awaitSingle()
    }
}

@Repository
interface MongoStorageMessageRepository : ReactiveMongoRepository<MongoStorageMessage, String> {
    fun findAllByProcessedTimestampIsNull(): Flux<MongoStorageMessage>
}
