package no.nb.mlt.wls.infrastructure.repositories.catalogMessage

import no.nb.mlt.wls.domain.model.catalogMessages.CatalogMessage
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "catalog-messages")
data class MongoCatalogMessage(
    @CreatedDate
    val createdTimestamp: Instant = Instant.now(),
    val processedTimestamp: Instant? = null,
    val body: CatalogMessage,
    @Id
    val id: String = body.id
)
