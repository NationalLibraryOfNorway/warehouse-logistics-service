package no.nb.mlt.wls.infrastructure.repositories.event

import no.nb.mlt.wls.domain.model.events.storage.StorageEvent
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.IndexDirection
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "storage-events")
data class MongoStorageEvent(
    @CreatedDate
    @Indexed(name = "created-timestamp-desc_index", direction = IndexDirection.DESCENDING)
    val createdTimestamp: Instant = Instant.now(),
    @Indexed(name = "processed-timestamp-desc_index", direction = IndexDirection.DESCENDING)
    val processedTimestamp: Instant? = null,
    val body: StorageEvent,
    @Id
    val id: String = body.id
)
