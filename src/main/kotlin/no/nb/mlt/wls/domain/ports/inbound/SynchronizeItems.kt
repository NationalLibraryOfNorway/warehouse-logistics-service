package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging

fun interface SynchronizeItems {
    suspend fun synchronizeItems(items: List<ItemToSynchronize>)

    data class ItemToSynchronize(
        val hostId: String,
        val hostName: HostName,
        val description: String,
        val location: String?,
        val quantity: Int,
        val itemCategory: ItemCategory,
        val packaging: Packaging,
        val currentPreferredEnvironment: Environment
    )
}
