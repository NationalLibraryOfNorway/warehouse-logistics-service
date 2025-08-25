package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.HostName

fun interface ReportItemAsMissing {
    suspend fun reportItemMissing(
        hostName: HostName,
        hostId: String
    )
}
