package no.nb.mlt.wls.core.data

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

enum class HostName {
    AXIELL
}

fun throwIfInvalidClientName(
    clientName: String,
    hostName: HostName
) {
    if (clientName.uppercase() == hostName.name) return
    throw ResponseStatusException(
        HttpStatus.FORBIDDEN,
        "You do not have access to view resources owned by $hostName"
    )
}
