package no.nb.mlt.wls.infrastructure.synq

import no.nb.mlt.wls.domain.model.Owner

enum class SynqOwner {
    NB,
    AV
}

fun SynqOwner.toOwner(): Owner =
    when (this) {
        SynqOwner.NB -> Owner.NB
        SynqOwner.AV -> Owner.ARKIVVERKET
    }

fun Owner.toSynqOwner(): SynqOwner =
    when (this) {
        Owner.NB -> SynqOwner.NB
        Owner.ARKIVVERKET -> SynqOwner.AV
    }
