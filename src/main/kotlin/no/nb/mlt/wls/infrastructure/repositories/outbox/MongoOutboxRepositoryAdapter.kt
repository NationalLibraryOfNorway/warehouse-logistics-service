package no.nb.mlt.wls.infrastructure.repositories.outbox

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.domain.model.outboxMessages.OutboxMessage
import no.nb.mlt.wls.domain.ports.outbound.OutboxRepository
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
class MongoOutboxRepositoryAdapter(
    private val mongoOutboxRepository: MongoOutboxRepository
) : OutboxRepository {
    override suspend fun save(outboxMessage: OutboxMessage): OutboxMessage {
        val mongoMessage = MongoOutboxMessage(body = outboxMessage)
        return mongoOutboxRepository
            .save(mongoMessage)
            .map { it.body }
            .doOnEach { logger.info { "Saved outbox message: $it" } }
            .timeout(Duration.ofSeconds(8))
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while saving to outbox. Message: $outboxMessage"
                    } else {
                        "Error while saving to outbox"
                    }
                }
            }.awaitSingle()
    }

    override suspend fun getAll(): List<OutboxMessage> {
        return mongoOutboxRepository.findAll()
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
            .onErrorMap { RuntimeException("Could not fetch from outbox") }
            .awaitSingle()
    }

    override suspend fun getUnprocessedSortedByCreatedTime(): List<OutboxMessage> {
        TODO("Not yet implemented")
    }

    override suspend fun markAsProcessed(outboxMessage: OutboxMessage): OutboxMessage {
        TODO("Not yet implemented")
    }
}

@Repository
interface MongoOutboxRepository : ReactiveMongoRepository<MongoOutboxMessage, String>
