package no.nb.mlt.wls.infrastructure.synq

import org.springframework.web.server.ServerErrorException

data class SynqError(val errorCode: Int, val errorText: String) {
    class DuplicateItemException(override val cause: Throwable) : ServerErrorException("Product already exists in SynQ", cause)

    class DuplicateOrderException(override val cause: Throwable) : ServerErrorException("Order already exists in SynQ", cause)
}
