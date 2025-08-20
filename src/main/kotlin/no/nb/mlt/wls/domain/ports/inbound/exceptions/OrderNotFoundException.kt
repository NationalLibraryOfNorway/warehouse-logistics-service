package no.nb.mlt.wls.domain.ports.inbound.exceptions

class OrderNotFoundException(
    override val message: String
) : RuntimeException(message)
