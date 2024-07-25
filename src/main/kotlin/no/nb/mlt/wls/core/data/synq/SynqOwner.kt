package no.nb.mlt.wls.core.data.synq

import no.nb.mlt.wls.core.data.Owner

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
