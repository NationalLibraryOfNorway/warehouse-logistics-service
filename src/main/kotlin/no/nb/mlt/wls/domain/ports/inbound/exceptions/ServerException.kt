package no.nb.mlt.wls.domain.ports.inbound.exceptions

class ServerException(
    override val message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
