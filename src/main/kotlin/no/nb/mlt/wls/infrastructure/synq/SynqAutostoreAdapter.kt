package no.nb.mlt.wls.infrastructure.synq

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.exceptions.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import no.nb.mlt.wls.domain.ports.outbound.exceptions.DuplicateResourceException
import no.nb.mlt.wls.domain.ports.outbound.exceptions.ResourceNotFoundException
import no.nb.mlt.wls.infrastructure.config.TimeoutProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.net.URI
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
class SynqAutostoreAdapter(
    @param:Qualifier("nonProxyWebClient")
    private val webClient: WebClient,
    @param:Value($$"${synq.path.base}")
    private val baseUrl: String,
    private val timeoutProperties: TimeoutProperties
) : StorageSystemFacade {
    override suspend fun createItem(item: Item) {
        val uri = URI.create("$baseUrl/nbproducts")

        webClient
            .post()
            .uri(uri)
            .bodyValue(item.toSynqPayload())
            .retrieve()
            .toEntity(SynqError::class.java)
            .timeout(timeoutProperties.storage)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while creating item '${item.hostId}' for ${item.hostName} in SynQ"
                }
            }.onErrorResume(WebClientResponseException::class.java) { error ->
                val errorText = error.getResponseBodyAs(SynqError::class.java)?.errorText
                if (errorText != null && errorText.contains("Duplicate product")) {
                    Mono.error(SynqError.DuplicateProductException(error))
                } else {
                    Mono.error(error)
                }
            }.onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .onErrorComplete(SynqError.DuplicateProductException::class.java)
            .awaitSingle()
    }

    override suspend fun editItem(item: Item) {
        val product = item.toSynqPayload()
        val uri = URI.create("$baseUrl/nbproducts/${product.owner}/${product.productId}")

        webClient
            .put()
            .uri(uri)
            .bodyValue(product)
            .retrieve()
            .toEntity(SynqError::class.java)
            .timeout(timeoutProperties.storage)
            .doOnError(TimeoutException::class.java) {
                logger.error { "Timed out while editing item '${item.hostId}' for ${item.hostName} in SynQ" }
            }.onErrorMap(WebClientResponseException::class.java) { error ->
                if (error.statusCode.isSameCodeAs(HttpStatus.NOT_FOUND)) {
                    ResourceNotFoundException(error.message)
                } else {
                    createServerError(error)
                }
            }.awaitFirst()
    }

    override suspend fun createOrder(order: Order) {
        // Wrap the order in the way SynQ likes it
        val orders = SynqOrder(listOf(order.toAutostorePayload()))

        webClient
            .post()
            .uri(URI.create("$baseUrl/orders/batch"))
            .bodyValue(orders)
            .retrieve()
            .toEntity(SynqError::class.java)
            .timeout(timeoutProperties.storage)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while creating order '${order.hostOrderId}' for ${order.hostName} in SynQ"
                }
            }.onErrorResume(WebClientResponseException::class.java) { error ->
                val synqError = error.getResponseBodyAs(SynqError::class.java) ?: throw createServerError(error)
                if (synqError.errorCode == 1037 || synqError.errorCode == 1029) {
                    throw OrderNotFoundException(synqError.errorText)
                }
                if (synqError.errorText.contains("Duplicate order")) {
                    Mono.error(DuplicateResourceException("errorCode: ${synqError.errorCode}, errorText: ${synqError.errorText}", error))
                } else {
                    Mono.error(error)
                }
            }.onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .awaitSingle()
    }

    override suspend fun deleteOrder(
        orderId: String,
        hostName: HostName
    ) {
        // Special handling for Arkivverket is required for the storage systems
        val owner = toSynqOwner(hostName)
        val synqOrderId =
            computeOrderId(
                hostName = hostName,
                hostOrderId = orderId,
                type = SynqOrderPayload.SynqOrderType.AUTOSTORE
            )

        webClient
            .delete()
            .uri(URI.create("$baseUrl/orders/$owner/$synqOrderId"))
            .retrieve()
            .toEntity(SynqError::class.java)
            .timeout(timeoutProperties.storage)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while deleting order '$orderId' for $hostName in SynQ"
                }
            }.onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .awaitSingle()
    }

    override fun isInStorage(location: AssociatedStorage): Boolean = location == AssociatedStorage.AUTOSTORE

    // This adapter method is a no-op, since the item creation is handled by the standard SynQ adapter
    override fun canHandleItem(item: Item): Boolean = false
}
