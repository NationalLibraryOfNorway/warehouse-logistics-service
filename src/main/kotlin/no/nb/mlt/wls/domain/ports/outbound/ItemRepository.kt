package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item

/**
 * Defines an interface for interacting with a database to manage items.
 * Offers various functions for retrieving, creating, updating, and managing item records.
 *
 * @see Item
 */
interface ItemRepository {
    suspend fun getItem(
        hostName: HostName,
        hostId: String
    ): Item?

    suspend fun getItemById(hostId: String): List<Item>

    suspend fun getItemsByIds(
        hostName: HostName,
        hostIds: List<String>
    ): List<Item>

    suspend fun getAllItemsForHosts(hostnames: List<HostName>): List<Item>

    suspend fun createItem(item: Item): Item

    suspend fun doesEveryItemExist(ids: List<ItemId>): Boolean

    suspend fun moveItem(
        hostName: HostName,
        hostId: String,
        quantity: Int,
        location: String
    ): Item

    suspend fun updateLocationAndQuantity(
        hostId: String,
        hostName: HostName,
        location: String,
        quantity: Int
    ): Item

    data class ItemId(
        val hostName: HostName,
        val hostId: String
    )
}
