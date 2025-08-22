package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.exceptions.OrderNotFoundException

/**
 * A port for deleting orders from the storage system.
 * The implementation of this interface is responsible for deleting an order
 * identified by the host system and order ID.
 *
 * @throws OrderNotFoundException if the specified order does not exist in the system.
 * @see Order
 */
fun interface DeleteOrder {
    @Throws(OrderNotFoundException::class)
    suspend fun deleteOrder(
        hostName: HostName,
        hostOrderId: String
    )
}
