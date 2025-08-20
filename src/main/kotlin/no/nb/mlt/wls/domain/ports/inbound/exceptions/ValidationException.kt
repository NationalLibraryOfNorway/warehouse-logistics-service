package no.nb.mlt.wls.domain.ports.inbound.exceptions

class ValidationException(
    override val message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
