package no.nb.mlt.wls.domain.ports.outbound.exceptions

class ItemMetadataUpdateException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
