package no.nb.mlt.wls.infrastructure.repositories.outbox

import no.nb.mlt.wls.domain.ports.outbound.OutboxMessage
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "outbox")
data class MongoOutboxMessage (
    @CreatedDate
    val createdTimestamp: Instant = Instant.now(),
    val processedTimestamp: Instant? = null,
    val body: OutboxMessage
)
