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
        contactPerson: String,
        address: Order.Address?,
        note: String?,
        callbackUrl: String
    ): Order
}

class IllegalOrderStateException(override val message: String, cause: Throwable? = null) : RuntimeException(message, cause)
