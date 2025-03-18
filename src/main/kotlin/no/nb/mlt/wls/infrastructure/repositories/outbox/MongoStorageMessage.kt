package no.nb.mlt.wls.infrastructure.repositories.outbox

import no.nb.mlt.wls.domain.model.storageMessages.StorageMessage
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "storage-messages")
data class MongoStorageMessage(
    @CreatedDate
    val createdTimestamp: Instant = Instant.now(),
    val processedTimestamp: Instant? = null,
    val body: StorageMessage,
    @Id
    val id: String = body.id
)
