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
        hostName: HostName,
        hostOrderId: String
    )

    @Throws(StorageSystemException::class)
    suspend fun updateOrder(order: Order): Order
}

class StorageSystemException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class DuplicateResourceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
