package no.nb.mlt.wls.infrastructure.repositories.event

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.domain.model.catalogEvents.CatalogEvent
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.RepositoryException
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.data.mongodb.repository.Update
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
class MongoCatalogEventRepositoryAdapter(
    private val mongoCatalogMessageRepository: MongoCatalogMessageRepository
) : EventRepository<CatalogEvent> {
    override suspend fun save(event: CatalogEvent): CatalogEvent {
        return mongoCatalogMessageRepository
            .save(MongoCatalogEvent(body = event))
            .map { it.body }
            .doOnEach { logger.info { "Saved catalog event to catalog outbox: $it" } }
            .timeout(Duration.ofSeconds(8))
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while saving event to catalog outbox. Event: $event"
                    } else {
                        "Error while saving event to catalog outbox. Event: $event"
                    }
                }
            }
            .onErrorMap { RepositoryException("Could not save event to catalog outbox", it) }
            .awaitSingle()
    }

    override suspend fun getAll(): List<CatalogEvent> {
        return mongoCatalogMessageRepository.findAll()
            .map { it.body }
            .collectList()
            .timeout(Duration.ofSeconds(8))
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while fetching events from catalog outbox"
                    } else {
                        "Error while fetching events from catalog outbox"
                    }
                }
            }
            .onErrorMap { RepositoryException("Could not fetch event from catalog outbox", it) }
            .awaitSingle()
    }

    override suspend fun getUnprocessedSortedByCreatedTime(): List<CatalogEvent> {
        return mongoCatalogMessageRepository
            .findAllByProcessedTimestampIsNull()
            .map { it.body }
            .collectList()
            .timeout(Duration.ofSeconds(8))
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while fetching unprocessed events from catalog outbox"
                    } else {
                        "Error while fetching unprocessed events from catalog outbox"
                    }
                }
            }
            .onErrorMap { RepositoryException("Could not fetch unprocessed events from catalog outbox", it) }
            .awaitSingle()
    }

    override suspend fun markAsProcessed(event: CatalogEvent): CatalogEvent {
        val updatedCunt =
            mongoCatalogMessageRepository
                .findAndUpdateProcessedTimestampById(event.id, Instant.now())
                .timeout(Duration.ofSeconds(8))
                .doOnError {
                    logger.error(it) {
                        if (it is TimeoutException) {
                            "Timed out while marking event as processed in catalog outbox. Event: $event"
                        } else {
                            "Error while marking event as processed in catalog outbox. Event: $event"
                        }
                    }
                }
                .onErrorMap { RepositoryException("Could not mark event as processed in catalog outbox", it) }
                .awaitSingle()

        if (updatedCunt != 0L) {
            return event
        } else {
            throw RepositoryException("No event found to update it as processed in catalog outbox. Event: $event")
        }
    }
}

@Repository
interface MongoCatalogMessageRepository : ReactiveMongoRepository<MongoCatalogEvent, String> {
    fun findAllByProcessedTimestampIsNull(): Flux<MongoCatalogEvent>

    @Query("{_id: ?0}")
    @Update("{'\$set':{processedTimestamp: ?1}}")
    fun findAndUpdateProcessedTimestampById(
        id: String,
        processedTimestamp: Instant
    ): Mono<Long>
}
