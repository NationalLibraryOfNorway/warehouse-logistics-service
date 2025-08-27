package no.nb.mlt.wls.domain.ports.outbound.exceptions

class StorageSystemException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
