package no.nb.mlt.wls.infrastructure.synq

import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ServerErrorException

data class SynqError(val errorCode: Int, val errorText: String) {
    companion object {
        /**
         * Converts a WebClient error into a ServerErrorException.
         * This is used for propagating error data to the client.
         * @see ServerErrorException
         * @see no.nb.mlt.wls.order.service.SynqOrderService
         * @see no.nb.mlt.wls.product.service.SynqProductService
         */
        fun createServerError(error: WebClientResponseException): StorageSystemException {
            val errorBody = error.getResponseBodyAs(SynqError::class.java)

            return StorageSystemException(
                """
                While communicating with SynQ API, an error occurred with code:
                '${errorBody?.errorCode ?: "NO ERROR CODE FOUND"}'
                and error text:
                '${errorBody?.errorText ?: "NO ERROR TEXT FOUND"}'.
                A copy of the original exception is attached to this error.
                """.trimIndent(),
                error
            )
        }
    }

    class DuplicateProductException(override val cause: Throwable) : ServerErrorException("Product already exists in SynQ", cause)

    // TODO - Move out of here
    class StorageSystemException(message: String, error: WebClientResponseException) : RuntimeException(message, error)
}
