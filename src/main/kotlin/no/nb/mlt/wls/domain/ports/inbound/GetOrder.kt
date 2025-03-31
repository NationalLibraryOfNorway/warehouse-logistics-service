package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order

fun interface GetOrder {
    @Throws(OrderNotFoundException::class)
    suspend fun getOrder(
        hostName: HostName,
        hostOrderId: String
    ): Order?
}
