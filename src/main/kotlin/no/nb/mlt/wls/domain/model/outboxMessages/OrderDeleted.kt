package no.nb.mlt.wls.domain.model.outboxMessages

import no.nb.mlt.wls.domain.model.HostName
import java.util.UUID

data class OrderDeleted(
    val host: HostName,
    val hostOrderId: String,
    override val id: String = UUID.randomUUID().toString()
) : OutboxMessage {
    override val body: Any
        get() = OrderId(host, hostOrderId)

    data class OrderId(
        val hostName: HostName,
        val hostOrderId: String
    )
}
