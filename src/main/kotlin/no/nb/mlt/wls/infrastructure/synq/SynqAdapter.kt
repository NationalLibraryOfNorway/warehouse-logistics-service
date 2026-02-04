package no.nb.mlt.wls.infrastructure.synq

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.domain.ports.inbound.exceptions.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.outbound.exceptions.DuplicateResourceException
import no.nb.mlt.wls.infrastructure.config.TimeoutProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.toEntity
import reactor.core.publisher.Mono
import java.net.URI
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
class SynqAdapter(
    @param:Qualifier("nonProxyWebClient")
    private val webClient: WebClient,
    @param:Value($$"${synq.path.base}")
    private val baseUrl: String,
    private val timeoutProperties: TimeoutProperties
) {
    suspend fun createItem(synqProductPayload: SynqProductPayload) {
        val uri = URI.create("$baseUrl/nbproducts")
        webClient
            .post()
            .uri(uri)
            .bodyValue(synqProductPayload)
            .retrieve()
            .toEntity(SynqError::class.java)
            .timeout(timeoutProperties.storage)
            .doOnError {
                if (it is TimeoutException) {
                    logger.error { "Timed out while creating item '${synqProductPayload.productId}' for ${synqProductPayload.hostName} in SynQ" }
                } else {
                    logger.error(it) { "Error while creating item '${synqProductPayload.productId}' for ${synqProductPayload.hostName} in SynQ" }
                }
            }.onErrorResume(WebClientResponseException::class.java) { error ->
                val errorText = error.getResponseBodyAs(SynqError::class.java)?.errorText ?: throw createServerError(error)
                // No better way of checking for duplicate errors in SynQ
                if (errorText.contains("Duplicate product")) {
                    // Log and modify error to an DuplicateItemException
                    logger.warn { "Trying to create a duplicate item in SynQ!" }
                    Mono.error(SynqError.DuplicateProductException(error))
                } else {
                    // Pass the error along with just simple logging
                    logger.error { "Some other error occurred! Text is as follows: $errorText" }
                    Mono.error(error)
                }
            }.onErrorMap(WebClientResponseException::class.java) {
                // Convert non-duplicate errors into server errors, only these will throw
                createServerError(it)
            }.onErrorComplete(SynqError.DuplicateProductException::class.java) // Treat duplicate errors as non-issues
            .awaitFirstOrNull()
    }

    suspend fun createOrder(synqOrderPayload: SynqOrderPayload) {
        val uri = URI.create("$baseUrl/orders/batch")
        // Wrap the order like SynQ likes it
        val synqOrder = SynqOrder(listOf(synqOrderPayload))
        webClient

            .post()
            .uri(uri)
            .bodyValue(synqOrder)
            .retrieve()
            .toEntity<SynqError>()
            .timeout(timeoutProperties.storage)
            .doOnError {
                if (it is TimeoutException) {
                    logger.error { "Timed out while creating order '${synqOrderPayload.orderId}' in SynQ" }
                } else {
                    logger.error(it) { "Error while creating order '${synqOrderPayload.orderId}' in SynQ" }
                }
            }.onErrorResume(WebClientResponseException::class.java) { error ->
                val synqError = error.getResponseBodyAs(SynqError::class.java) ?: throw createServerError(error)

                // Convert specific errors to other types for better info
                if (synqError.errorCode == 1037 || synqError.errorCode == 1029) {
                    logger.error {
                        "Items in order  ${synqOrderPayload.orderId}' are not found in SynQ, error text: ${synqError.errorText}"
                    }
                    throw OrderNotFoundException(synqError.errorText)
                }
                if (synqError.errorText.contains("Duplicate order")) {
                    logger.error { "Order ${synqOrderPayload.orderId}' in SynQ already exists, error text: ${synqError.errorText}" }
                    throw DuplicateResourceException("errorCode: ${synqError.errorCode}, errorText: ${synqError.errorText}", error)
                } else {
                    // Pass other errors along with just simple logging
                    logger.error { "Some other error occurred! Code: ${synqError.errorCode}, text: ${synqError.errorText}" }
                    Mono.error(error)
                }
            }.onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .awaitSingle()
    }

    suspend fun deleteOrder(
        owner: SynqOwner,
        synqOrderId: String
    ) {
        webClient
            .delete()
            .uri(URI.create("$baseUrl/orders/$owner/$synqOrderId"))
            .retrieve()
            .toEntity(SynqError::class.java)
            .timeout(timeoutProperties.storage)
            .doOnError {
                if (it is TimeoutException) {
                    logger.error { "Timed out while deleting order '$synqOrderId' in SynQ" }
                } else {
                    logger.error(it) { "Error while deleting order '$synqOrderId' in SynQ" }
                }
            }.onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .awaitSingle()
    }
}
