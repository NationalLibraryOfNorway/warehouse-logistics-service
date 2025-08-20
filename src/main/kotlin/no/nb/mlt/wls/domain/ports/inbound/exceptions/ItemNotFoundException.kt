package no.nb.mlt.wls.domain.ports.inbound.exceptions

class ItemNotFoundException(
    override val message: String
) : RuntimeException(message)
