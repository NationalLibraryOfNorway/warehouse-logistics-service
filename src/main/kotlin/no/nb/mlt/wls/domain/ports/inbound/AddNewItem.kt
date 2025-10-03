package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.model.Storage
import no.nb.mlt.wls.domain.model.UNKNOWN_LOCATION

/**
 * A port for adding new items into the system.
 *
 * This port accepts metadata about the item and returns a fully created [Item] instance.
 * It is designed to manage the lifecycle of adding new items by converting item metadata
 * into a concrete representation that can be stored and managed within the system.
 *
 * The implementing function is expected to perform transformations between [ItemMetadata]
 * and [Item], along with any associated business logic such as validation, saving, or initialization
 * of the item's state in the storage system.
 *
 * @see ItemMetadata
 * @see Item
 */
fun interface AddNewItem {
    suspend fun addItem(itemMetadata: ItemMetadata): Item
}

/**
 * Represents metadata for an item in the storage system. This class contains high-level
 * information related to an item that can be used for creating an item record or interacting
 * with other systems.
 *
 * @property hostId The unique identifier of the item in its respective host system.
 * @property hostName The name of the host system that owns the item.
 * @property description A textual description providing more details about the item.
 * @property itemCategory The category or type of the item, used to classify items in storage.
 * @property preferredEnvironment This item's preferred storage environment.
 * @property packaging The type of packaging the item is stored in.
 * @property callbackUrl An optional URL used for callbacks related to this item.
 * @see Item
 */
data class ItemMetadata(
    val hostId: String,
    val hostName: HostName,
    val description: String,
    val itemCategory: ItemCategory,
    val preferredEnvironment: Environment,
    val packaging: Packaging,
    val callbackUrl: String?
) {
    fun toItem(
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
        quantity,
        Storage.UNKNOWN
    )
}
