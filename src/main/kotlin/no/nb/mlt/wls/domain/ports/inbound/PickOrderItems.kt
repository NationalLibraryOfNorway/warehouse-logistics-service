package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName

/**
 * This interface is used to handle when an items from an Order has been picked, which
 * indicates that the status in the Order Items need to be updated
 */
fun interface PickOrderItems {
    suspend fun pickOrderItems(hostName: HostName, pickedHostIds: List<String>, orderId: String)
}
