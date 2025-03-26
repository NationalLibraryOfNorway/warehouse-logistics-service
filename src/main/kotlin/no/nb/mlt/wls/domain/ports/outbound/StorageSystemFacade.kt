package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order

interface StorageSystemFacade {
    @Throws(StorageSystemException::class)
    suspend fun createItem(item: Item)

    @Throws(DuplicateResourceException::class)
    suspend fun createOrder(order: Order)

    suspend fun deleteOrder(
        orderId: String,
        hostName: HostName
    )

    @Throws(StorageSystemException::class)
    suspend fun updateOrder(order: Order): Order

    suspend fun canHandleLocation(location: String): Boolean

    fun canHandleItem(item: Item): Boolean

    companion object {
        /**
         * Used to split hostname and the host order ID when being sent to storage systems.
         * This is due to hosts potentially sharing the same IDs between each other (E.G. numeric ids)
         */
        const val DELIMITER = "---"
    }
}

class StorageSystemException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class DuplicateResourceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
