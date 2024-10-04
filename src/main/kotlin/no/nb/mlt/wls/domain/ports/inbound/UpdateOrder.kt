package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.OrderUpdateException
import kotlin.jvm.Throws

interface UpdateOrder {
    @Throws(OrderNotFoundException::class, ValidationException::class, OrderUpdateException::class, IllegalOrderStateException::class)
    suspend fun updateOrder(
        hostName: HostName,
        hostOrderId: String,
        itemHostIds: List<String>,
        orderType: Order.Type,
        receiver: Order.Receiver,
        callbackUrl: String
    ): Order

    // TODO - Should this be split off into its own use-case/interface?
    @Throws(OrderNotFoundException::class)
    suspend fun updateOrderStatus(
        hostName: HostName,
        hostOrderId: String,
        status: String
    ): Order
}

class IllegalOrderStateException(override val message: String, cause: Throwable? = null) : RuntimeException(message, cause)
