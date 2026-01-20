package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.model.UNKNOWN_LOCATION

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
        val currentPreferredEnvironment: Environment,
        val associatedStorage: AssociatedStorage,
        val confidential: Boolean
    ) {
        fun toItem() =
            Item(
                hostId = this.hostId,
                hostName = this.hostName,
                description = this.description,
                itemCategory = this.itemCategory,
                preferredEnvironment = this.currentPreferredEnvironment,
                packaging = this.packaging,
                callbackUrl = null,
                location = this.location ?: UNKNOWN_LOCATION,
                quantity = this.quantity,
                associatedStorage = this.associatedStorage,
                confidential = this.confidential
            )
    }
}
