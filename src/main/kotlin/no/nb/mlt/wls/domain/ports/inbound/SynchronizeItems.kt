package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName

interface SynchronizeItems {
    fun synchronizeItems(items: List<ItemToSynchronize>)

    data class ItemToSynchronize(
        val hostId: String,
        val hostName: HostName,
        val location: String,
        val quantity: Int
    )
}
