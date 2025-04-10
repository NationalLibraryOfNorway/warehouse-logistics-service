package no.nb.mlt.wls.infrastructure.repositories.event

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.domain.TimeoutProperties
import no.nb.mlt.wls.domain.model.events.storage.StorageEvent
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.RepositoryException
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.data.mongodb.repository.Update
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
class MongoStorageEventRepositoryAdapter(
    private val mongoStorageEventRepository: MongoStorageEventRepository,
    private val timeoutConfig: TimeoutProperties
) : EventRepository<StorageEvent> {
    override suspend fun save(event: StorageEvent): StorageEvent =
        mongoStorageEventRepository
            .save(MongoStorageEvent(body = event))
            .map { it.body }
            .doOnEach { logger.info { "Saved storage event to outbox: $it" } }
            .timeout(timeoutConfig.mongo)
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while saving event to storage outbox. Event: $event"
                    } else {
                        "Error while saving event to storage outbox. Event: $event"
                    }
                }
            }.onErrorMap { RepositoryException("Could not save event to storage outbox", it) }
            .awaitSingle()

    override suspend fun getAll(): List<StorageEvent> =
        mongoStorageEventRepository
            .findAll()
            .sort { e1, e2 -> e1.createdTimestamp.compareTo(e2.createdTimestamp) }
            .map { it.body }
            .collectList()
            .timeout(timeoutConfig.mongo)
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while fetching events from storage outbox"
                    } else {
                        "Error while fetching events from storage outbox"
                    }
                }
            }.onErrorMap { RepositoryException("Could not fetch event from storage outbox", it) }
            .awaitSingle()

    override suspend fun getUnprocessedSortedByCreatedTime(): List<StorageEvent> =
        mongoStorageEventRepository
            .findAllByProcessedTimestampIsNull()
            .sort { e1, e2 -> e1.createdTimestamp.compareTo(e2.createdTimestamp) }
            .map { it.body }
            .collectList()
            .timeout(timeoutConfig.mongo)
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while fetching unprocessed events from storage outbox"
                    } else {
                        "Error while fetching unprocessed events from storage outbox"
                    }
                }
            }.onErrorMap { RepositoryException("Could not fetch unprocessed events from storage outbox", it) }
            .awaitSingle()

    override suspend fun markAsProcessed(event: StorageEvent): StorageEvent {
        val updatedRecordCount =
            mongoStorageEventRepository
                .findAndUpdateProcessedTimestampById(event.id, Instant.now())
                .timeout(timeoutConfig.mongo)
                .doOnError {
                    logger.error(it) {
                        if (it is TimeoutException) {
                            "Timed out while marking event as processed in storage outbox. Event: $event"
                        } else {
                            "Error while marking event as processed in storage outbox. Event: $event"
                        }
                    }
                }.onErrorMap { RepositoryException("Could not mark event as processed in storage outbox", it) }
                .awaitSingle()

        if (updatedRecordCount != 0L) {
            return event
        } else {
            throw RepositoryException("No event found to update it as processed in storage outbox. Event: $event")
        }
    }
}

@Repository
interface MongoStorageEventRepository : ReactiveMongoRepository<MongoStorageEvent, String> {
    fun findAllByProcessedTimestampIsNull(): Flux<MongoStorageEvent>

    @Query("{_id: ?0}")
    @Update("{'\$set':{processedTimestamp: ?1}}")
    fun findAndUpdateProcessedTimestampById(
        id: String,
        processedTimestamp: Instant
    ): Mono<Long>
}
