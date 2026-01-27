package no.nb.mlt.wls.domain.ports.outbound.exceptions

class ResourceNotFoundException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
