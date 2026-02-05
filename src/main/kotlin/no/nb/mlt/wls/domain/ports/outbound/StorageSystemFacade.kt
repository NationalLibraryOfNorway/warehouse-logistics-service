package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.exceptions.DuplicateResourceException
import no.nb.mlt.wls.domain.ports.outbound.exceptions.ResourceNotFoundException
import no.nb.mlt.wls.domain.ports.outbound.exceptions.StorageSystemException

/**
 * When creating orders, we need to ensure their IDs are unique in storage systems.
 * So each order ID is modified to be: <HOST_NAME>---<HOST_ORDER_ID>.
 * This constant holds the value of the delimiter so we can ensure it is consistent, and so we can change it as need be.
 * This is motivated by the fact that two catalogs can come up with the same order IDs.
 */
const val DELIMITER = "---"

/**
 * A facade interface for operations related to the storage system. It provides methods to manage
 * items and orders, check if certain locations can be handled, and evaluate item compatibility.
 */
interface StorageSystemFacade {
    @Throws(StorageSystemException::class)
    suspend fun createItem(item: Item)

    @Throws(DuplicateResourceException::class)
    suspend fun createOrder(order: Order)

    @Throws(StorageSystemException::class, ResourceNotFoundException::class)
    suspend fun editItem(item: Item)

    suspend fun deleteOrder(
        orderId: String,
        hostName: HostName
    )

    fun isInStorage(location: AssociatedStorage): Boolean

    fun canHandleItem(item: Item): Boolean

    fun getName(): String
}
