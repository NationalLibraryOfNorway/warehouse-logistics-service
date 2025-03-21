package no.nb.mlt.wls.infrastructure.repositories.event

import no.nb.mlt.wls.domain.model.catalogEvents.CatalogEvent
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "catalog-events")
data class MongoCatalogEvent(
    @CreatedDate
    val createdTimestamp: Instant = Instant.now(),
    val processedTimestamp: Instant? = null,
    val body: CatalogEvent,
    @Id
    val id: String = body.id
)
