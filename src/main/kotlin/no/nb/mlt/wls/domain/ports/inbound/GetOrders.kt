package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order

fun interface GetOrders {
    suspend fun getAllOrders(hostnames: List<HostName>): List<Order>
}
