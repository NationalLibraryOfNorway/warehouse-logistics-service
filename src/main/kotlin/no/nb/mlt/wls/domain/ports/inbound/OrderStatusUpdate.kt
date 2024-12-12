package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import kotlin.jvm.Throws

fun interface OrderStatusUpdate {
    @Throws(OrderNotFoundException::class)
    suspend fun updateOrderStatus(
        hostName: HostName,
        hostOrderId: String,
        status: Order.Status
    ): Order
}
