package no.nb.mlt.wls.infrastructure.synq

import no.nb.mlt.wls.domain.Owner

enum class SynqOwner {
    NB
}

fun SynqOwner.toOwner(): Owner =
    when (this) {
        SynqOwner.NB -> Owner.NB
    }

fun Owner.toSynqOwner(): SynqOwner =
    when (this) {
        Owner.NB -> SynqOwner.NB
    }
