package no.nb.mlt.wls.domain.ports.inbound.exceptions

class IllegalOrderStateException(
    override val message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
