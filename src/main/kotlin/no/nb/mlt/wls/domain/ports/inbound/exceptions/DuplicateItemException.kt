package no.nb.mlt.wls.domain.ports.inbound.exceptions

class DuplicateItemException(
    override val message: String
) : RuntimeException(message)
