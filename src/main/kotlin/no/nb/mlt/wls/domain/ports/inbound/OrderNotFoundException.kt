package no.nb.mlt.wls.domain.ports.inbound

class OrderNotFoundException(override val message: String) : RuntimeException(message)
