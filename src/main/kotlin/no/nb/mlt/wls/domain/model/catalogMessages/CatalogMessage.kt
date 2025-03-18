package no.nb.mlt.wls.domain.model.catalogMessages

import java.time.Instant

sealed interface CatalogMessage {
    val id: String
    val body: Any
    val messageTimestamp: Instant
}
