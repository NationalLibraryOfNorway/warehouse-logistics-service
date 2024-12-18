package no.nb.mlt.wls.infrastructure.synq

import no.nb.mlt.wls.domain.model.HostName

enum class SynqOwner {
    NB,
    AV
}

fun toSynqOwner(hostName: HostName): SynqOwner {
    return when (hostName) {
        HostName.ASTA -> SynqOwner.AV
        else -> SynqOwner.NB
    }
}
