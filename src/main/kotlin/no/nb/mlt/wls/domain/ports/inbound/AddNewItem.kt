package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.model.Packaging

interface AddNewItem {
    suspend fun addItem(itemMetadata: ItemMetadata): Item
}

data class ItemMetadata(
    val hostId: String,
    val hostName: HostName,
    val description: String,
    val productCategory: String,
    val preferredEnvironment: Environment,
    val packaging: Packaging,
    val owner: Owner
)

fun ItemMetadata.toItem(
    quantity: Double? = 0.0,
    location: String? = null
) = Item(
    this.hostId,
    this.hostName,
    this.description,
    this.productCategory,
    this.preferredEnvironment,
    this.packaging,
    this.owner,
    location,
    quantity
)
