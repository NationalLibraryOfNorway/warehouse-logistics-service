package no.nb.mlt.wls.infrastructure.synq

import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.ports.outbound.exceptions.StorageSystemException
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * Converts a WebClient error into a StorageSystemException.
 * This is used for propagating error data to the client.
 * @see StorageSystemException
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

/**
 * Computes the associated storage from the Synq-specific location.
 * Smelly code is inevitable...
 */
fun computeAssociatedStorage(location: String): AssociatedStorage =
    if (location.uppercase() == "AUTOSTORE_WAREHOUSE") AssociatedStorage.AUTOSTORE else AssociatedStorage.SYNQ
