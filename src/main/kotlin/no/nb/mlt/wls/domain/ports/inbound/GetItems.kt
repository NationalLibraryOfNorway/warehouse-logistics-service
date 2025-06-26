package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item

fun interface GetItems {
    suspend fun getAllItems(hostnames: List<HostName>): List<Item>
}
