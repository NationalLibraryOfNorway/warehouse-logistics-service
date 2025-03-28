package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item

interface ItemRepository {
    suspend fun getItem(
        hostName: HostName,
        hostId: String
    ): Item?

    suspend fun getItems(
        hostName: HostName,
        hostIds: List<String>
    ): List<Item>

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

    // TODO - This is MongoDB specific code, and should be moved into infrastructure instead
    data class ItemId(val hostName: HostName, val hostId: String)
}

class ItemMovingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
