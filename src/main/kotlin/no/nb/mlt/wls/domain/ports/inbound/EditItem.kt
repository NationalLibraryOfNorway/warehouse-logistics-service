package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging

/**
 * A port for editing items in the system.
 *
 * This port accepts an [Item] instance and new metadata which will replace the old metadata
 * and returns an updated [Item]. It is designed to allow for the editing of items in the system, in cases
 * where the item's metadata was wrong, or we got info about item after an order was made. The second use case
 * exists because if we get an order with items we don't know, we will create a mostly empty [Item] with only
 * the ID and host name set. This function lets catalogs update such items.
 *
 * The implementing function is expected to perform transformations between [ItemEditMetadata]
 * and [Item], along with any associated business logic such as validation, saving, or initialization
 * of the item's state in the storage system.
 *
 * @see ItemEditMetadata
 * @see Item
 */
fun interface EditItem {
    suspend fun editItem(
        item: Item,
        newMetadata: ItemEditMetadata
    ): Item
}

/**
 * Represents editable metadata for an item in the storage system. This contains only info that
 * we allow catalogs to change about an item.
 *
 * @property description A textual description providing more details about the item.
 * @property itemCategory The category or type of the item, used to classify items in storage.
 * @property preferredEnvironment This item's preferred storage environment.
 * @property packaging The type of packaging the item is stored in.
 * @property callbackUrl An optional URL used for callbacks related to this item.
 * @see Item
 */
data class ItemEditMetadata(
    val description: String,
    val itemCategory: ItemCategory,
    val preferredEnvironment: Environment,
    val packaging: Packaging,
    val callbackUrl: String?
)
