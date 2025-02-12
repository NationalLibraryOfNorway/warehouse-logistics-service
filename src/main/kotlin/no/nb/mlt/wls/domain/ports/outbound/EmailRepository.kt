package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.HostEmail
import no.nb.mlt.wls.domain.model.HostName

interface EmailRepository {
    suspend fun createHostEmail(
        hostName: HostName,
        email: String
    )

    suspend fun getHostEmail(hostName: HostName): HostEmail?

    suspend fun getHostEmails(hosts: List<HostName>): List<HostEmail>
}
