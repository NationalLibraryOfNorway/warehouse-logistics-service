package no.nb.mlt.wls.infrastructure.repositories.event

import no.nb.mlt.wls.domain.model.events.email.EmailEvent
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "email-events")
data class MongoEmailEvent(
    @CreatedDate
    val createdTimestamp: Instant = Instant.now(),
    val processedTimestamp: Instant? = null,
    val body: EmailEvent,
    @Id
    val id: String = body.id
)
