package no.nb.mlt.wls.domain.model.catalogEvents

import no.nb.mlt.wls.domain.model.Event
import java.time.Instant

sealed interface CatalogEvent : Event {
    val eventTimestamp: Instant
}
