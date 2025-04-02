package no.nb.mlt.wls.domain.ports.inbound

class ServerException(
    override val message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
