package no.nb.mlt.wls.domain.model.events.catalog

import no.nb.mlt.wls.domain.model.events.Event
import java.time.Instant

sealed interface CatalogEvent : Event {
    val eventTimestamp: Instant
}
