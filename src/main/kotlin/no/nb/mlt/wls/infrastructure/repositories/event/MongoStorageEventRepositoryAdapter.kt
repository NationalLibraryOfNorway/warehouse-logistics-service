package no.nb.mlt.wls.infrastructure.repositories.event

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.domain.model.storageEvents.StorageEvent
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.RepositoryException
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
class MongoStorageEventRepositoryAdapter(
    private val mongoStorageMessageRepository: MongoStorageMessageRepository
) : EventRepository<StorageEvent> {
    override suspend fun save(event: StorageEvent): StorageEvent {
        return mongoStorageMessageRepository
            .save(MongoStorageEvent(body = event))
            .map { it.body }
            .doOnEach { logger.info { "Saved outbox message: $it" } }
            .timeout(Duration.ofSeconds(8))
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while saving to outbox. Message: $event"
                    } else {
                        "Error while saving to outbox"
                    }
                }
            }
            .onErrorMap { RepositoryException("Could not save to outbox", it) }
            .awaitSingle()
    }

    override suspend fun getAll(): List<StorageEvent> {
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
            .onErrorMap { RepositoryException("Could not fetch from outbox", it) }
            .awaitSingle()
    }

    override suspend fun getUnprocessedSortedByCreatedTime(): List<StorageEvent> {
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
            .onErrorMap { RepositoryException("Could not fetch unprocessed from outbox", it) }
            .awaitSingle()
    }

    override suspend fun markAsProcessed(event: StorageEvent): StorageEvent {
        return mongoStorageMessageRepository
            .save(MongoStorageEvent(body = event, processedTimestamp = Instant.now()))
            .map { it.body }
            .timeout(Duration.ofSeconds(8))
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while marking as processed in outbox. Message: $event"
                    } else {
                        "Error while marking as processed in outbox"
                    }
                }
            }
            .onErrorMap { RepositoryException("Could not mark as processed in outbox", it) }
            .awaitSingle()
    }
}

@Repository
interface MongoStorageMessageRepository : ReactiveMongoRepository<MongoStorageEvent, String> {
    fun findAllByProcessedTimestampIsNull(): Flux<MongoStorageEvent>
}
