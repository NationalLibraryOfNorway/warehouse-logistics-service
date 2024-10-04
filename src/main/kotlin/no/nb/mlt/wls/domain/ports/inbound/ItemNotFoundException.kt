package no.nb.mlt.wls.domain.ports.inbound

class ItemNotFoundException(override val message: String) : RuntimeException(message)
