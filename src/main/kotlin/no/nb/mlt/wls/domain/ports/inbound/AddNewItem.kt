package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.core.data.Environment
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
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

// create a runtime exception for timeout when fetching item from database
