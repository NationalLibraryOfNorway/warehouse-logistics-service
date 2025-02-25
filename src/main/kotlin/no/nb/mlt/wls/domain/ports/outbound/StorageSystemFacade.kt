package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order

interface StorageSystemFacade {
    @Throws(StorageSystemException::class)
    suspend fun createItem(item: Item)

    @Throws(DuplicateResourceException::class)
    suspend fun createOrder(order: Order)

    suspend fun deleteOrder(order: Order)

    @Throws(StorageSystemException::class)
    suspend fun updateOrder(order: Order): Order

    suspend fun canHandleLocation(location: String): Boolean

    fun canHandleItem(item: Item): Boolean
}

class StorageSystemException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class DuplicateResourceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
