package no.nb.mlt.wls.infrastructure.synq

import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.outbound.DuplicateResourceException
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemException
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ServerErrorException
import reactor.core.publisher.Mono
import java.net.URI

@Component
class SynqAdapter(
    private val webClient: WebClient,
    @Value("\${synq.path.base}")
    private val baseUrl: String
) : StorageSystemFacade {
    override suspend fun createItem(item: Item) {
        val uri = URI.create("$baseUrl/nbproducts")

        webClient
            .post()
            .uri(uri)
            .bodyValue(item.toSynqPayload())
            .retrieve()
            .toEntity(SynqError::class.java)
            .onErrorResume(WebClientResponseException::class.java) { error ->
                val errorText = error.getResponseBodyAs(SynqError::class.java)?.errorText
                if (errorText != null && errorText.contains("Duplicate product")) {
                    Mono.error(SynqError.DuplicateItemException(error))
                } else {
                    Mono.error(error)
                }
            }
            .onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .onErrorComplete(SynqError.DuplicateItemException::class.java)
            .awaitSingle()
    }

    override suspend fun createOrder(order: Order) {
        // Wrap the order in the way SynQ likes it
        val orders = SynqOrder(listOf(order.toSynqPayload()))

        webClient
            .post()
            .uri(URI.create("$baseUrl/orders/batch"))
            .bodyValue(orders)
            .retrieve()
            .toEntity(SynqError::class.java)
            .onErrorResume(WebClientResponseException::class.java) { error ->
                val synqError = error.getResponseBodyAs(SynqError::class.java) ?: throw createServerError(error)
                if (synqError.errorCode == 1037 || synqError.errorCode == 1029) {
                    throw OrderNotFoundException(synqError.errorText)
                }
                if (synqError.errorText.contains("Duplicate order")) {
                    Mono.error(DuplicateResourceException("errorCode: ${synqError.errorCode}, errorText: ${synqError.errorText}", error))
                } else {
                    Mono.error(error)
                }
            }
            .onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .awaitSingle()
    }

    override suspend fun deleteOrder(order: Order) {
        // Special handling for Arkivverket is required for the storage systems
        val owner = toSynqOwner(order.hostName)

        webClient
            .delete()
            .uri(URI.create("$baseUrl/orders/$owner/${order.hostOrderId}"))
            .retrieve()
            .toEntity(SynqError::class.java)
            .onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .awaitSingle()
    }

    override suspend fun updateOrder(order: Order): Order {
        return webClient
            .put()
            .uri(URI.create("$baseUrl/orders/batch"))
            .bodyValue(SynqOrder(listOf(order.toSynqPayload())))
            .retrieve()
            .toBodilessEntity()
            .map { order }
            .onErrorMap(WebClientResponseException::class.java, ::createServerError)
            .awaitSingle()
    }
}

/**
 * Converts a WebClient error into a ServerErrorException.
 * This is used for propagating error data to the client.
 * @see ServerErrorException
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
