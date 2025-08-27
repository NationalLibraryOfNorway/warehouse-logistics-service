package no.nb.mlt.wls.domain.ports.outbound.exceptions

class ItemMovingException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
