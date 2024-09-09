package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.Environment
import no.nb.mlt.wls.domain.HostName
import no.nb.mlt.wls.domain.Owner
import no.nb.mlt.wls.domain.Packaging
import no.nb.mlt.wls.domain.Item

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
