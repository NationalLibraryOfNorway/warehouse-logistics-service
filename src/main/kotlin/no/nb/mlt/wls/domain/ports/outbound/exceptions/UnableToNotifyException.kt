package no.nb.mlt.wls.domain.ports.outbound.exceptions

class UnableToNotifyException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
