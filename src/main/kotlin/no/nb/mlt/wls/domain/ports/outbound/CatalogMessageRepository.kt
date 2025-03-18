package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.catalogMessages.CatalogMessage

interface CatalogMessageRepository {
    suspend fun save(catalogMessage: CatalogMessage): CatalogMessage

    suspend fun getAll(): List<CatalogMessage>

    suspend fun getUnprocessedSortedByCreatedTime(): List<CatalogMessage>

    suspend fun markAsProcessed(catalogMessage: CatalogMessage): CatalogMessage

}
