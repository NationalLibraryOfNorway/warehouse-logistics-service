package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import kotlin.jvm.Throws

fun interface DeleteOrder {
    @Throws(OrderNotFoundException::class)
    suspend fun deleteOrder(
        hostName: HostName,
        hostOrderId: String
    )
}
