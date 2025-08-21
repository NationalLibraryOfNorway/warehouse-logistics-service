package no.nb.mlt.wls.domain.ports.outbound.exceptions

class DuplicateResourceException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
