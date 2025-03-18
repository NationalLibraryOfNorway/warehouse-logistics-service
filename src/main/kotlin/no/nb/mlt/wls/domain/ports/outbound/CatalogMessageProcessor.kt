package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.catalogMessages.CatalogMessage

fun interface CatalogMessageProcessor {
    suspend fun handleEvent(event: CatalogMessage)
}
