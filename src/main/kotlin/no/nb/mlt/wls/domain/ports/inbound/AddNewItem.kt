package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.model.UNKNOWN_LOCATION

fun interface AddNewItem {
    suspend fun addItem(itemMetadata: ItemMetadata): Item
}

data class ItemMetadata(
    val hostId: String,
    val hostName: HostName,
    val description: String,
    val itemCategory: ItemCategory,
    val preferredEnvironment: Environment,
    val packaging: Packaging,
    val callbackUrl: String?
)

/**
 * Maps and creates an Item from ItemMetadata
 * Note that when registering products the quantity and location
 * are always zero/empty, as they must be registered before being inserted into storage
 */
fun ItemMetadata.toItem(
    quantity: Int = 0,
    location: String = UNKNOWN_LOCATION
) = Item(
    this.hostId,
    this.hostName,
    this.description,
    this.itemCategory,
    this.preferredEnvironment,
    this.packaging,
    this.callbackUrl,
    location,
    quantity
)
