package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item

fun interface GetItem {
    suspend fun getItem(
        hostName: HostName,
        hostId: String
    ): Item?
}
