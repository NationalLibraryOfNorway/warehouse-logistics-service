package no.nb.mlt.wls.domain.ports.outbound

class RepositoryException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
