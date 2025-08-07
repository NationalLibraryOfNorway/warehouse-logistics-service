package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order

/**
 * When creating orders, we need to ensure their IDs are unique in storage systems.
 * So each order ID is modified to be: <HOST_NAME>---<HOST_ORDER_ID>.
 * This constant holds the value of delimiter so we can ensure the same is used everywhere, and so we can change it as need be.
 * It's motivated by the fact that two catalogs can come up with the same order IDs.
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

    suspend fun deleteOrder(
        orderId: String,
        hostName: HostName
    )

    suspend fun canHandleLocation(location: String): Boolean

    fun canHandleItem(item: Item): Boolean
}

class StorageSystemException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class DuplicateResourceException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class NotSupportedException(
    message: String
) : RuntimeException(message)
