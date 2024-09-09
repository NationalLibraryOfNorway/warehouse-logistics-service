package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.Item
import no.nb.mlt.wls.infrastructure.synq.SynqError

interface StorageSystemFacade {
    @Throws(SynqError.StorageSystemException::class)
    suspend fun createItem(item: Item)
}
