package no.nb.mlt.wls.infrastructure.repositories.catalogMessage

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.domain.model.catalogMessages.CatalogMessage
import no.nb.mlt.wls.domain.ports.outbound.CatalogMessageRepository
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
class MongoCatalogMessageRepositoryAdapter( // TODO What are Generics for 400 dollars, Alex
    private val mongoCatalogMessageRepository: MongoCatalogMessageRepository
) : CatalogMessageRepository {
    override suspend fun save(catalogMessage: CatalogMessage): CatalogMessage {
        val mongoMessage = MongoCatalogMessage(body = catalogMessage)
        return mongoCatalogMessageRepository
            .save(mongoMessage)
            .map { it.body }
            .doOnEach { logger.info { "Saved outbox message: $it" } }
            .timeout(Duration.ofSeconds(8))
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while saving to outbox. Message: $catalogMessage"
                    } else {
                        "Error while saving to outbox"
                    }
                }
            }
            .onErrorMap { RepositoryException("Could not save to outbox", it) }
            .awaitSingle()
    }

    override suspend fun getAll(): List<CatalogMessage> {
        return mongoCatalogMessageRepository.findAll()
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

    override suspend fun getUnprocessedSortedByCreatedTime(): List<CatalogMessage> {
        return mongoCatalogMessageRepository
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

    override suspend fun markAsProcessed(catalogMessage: CatalogMessage): CatalogMessage {
        return mongoCatalogMessageRepository
            .save(MongoCatalogMessage(body = catalogMessage, processedTimestamp = Instant.now()))
            .map { it.body }
            .timeout(Duration.ofSeconds(8))
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while marking as processed in outbox. Message: $catalogMessage"
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
interface MongoCatalogMessageRepository : ReactiveMongoRepository<MongoCatalogMessage, String> {
    fun findAllByProcessedTimestampIsNull(): Flux<MongoCatalogMessage>
}
