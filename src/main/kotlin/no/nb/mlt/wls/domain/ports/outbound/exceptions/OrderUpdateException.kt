package no.nb.mlt.wls.domain.ports.outbound.exceptions

class OrderUpdateException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
