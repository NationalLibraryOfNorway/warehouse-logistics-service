package no.nb.mlt.wls.infrastructure.synq

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.exceptions.ItemNotFoundException
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
class SynqStandardAdapter(
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
            .doOnError {
                if (it is TimeoutException) {
                    logger.error { "Timed out while creating item '${item.hostId}' for ${item.hostName} in SynQ" }
                } else {
                    logger.error(it) { "Error while creating item '${item.hostId}' for ${item.hostName} in SynQ" }
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

    override suspend fun createOrder(order: Order) {
        // Wrap the order in the way SynQ likes it
        val orders = SynqOrder(listOf(order.toSynqStandardPayload()))

        webClient
            .post()
            .uri(URI.create("$baseUrl/orders/batch"))
            .bodyValue(orders)
            .retrieve()
            .toEntity(SynqError::class.java)
            .timeout(timeoutProperties.storage)
            .doOnError {
                if (it is TimeoutException) {
                    logger.error { "Timed out while creating order '${order.hostOrderId}' for ${order.hostName} in SynQ" }
                } else {
                    logger.error(it) { "Error while creating order '${order.hostOrderId}' for ${order.hostName} in SynQ" }
                }
            }.onErrorResume(WebClientResponseException::class.java) { error ->
                val synqError = error.getResponseBodyAs(SynqError::class.java) ?: throw createServerError(error)

                // Convert specific errors to other types for better info
                if (synqError.errorCode == 1037 || synqError.errorCode == 1029) {
                    logger.error {
                        "Items in order  ${order.hostOrderId}' for ${order.hostName} are not found in SynQ, error text: ${synqError.errorText}"
                    }
                    throw OrderNotFoundException(synqError.errorText)
                }
                if (synqError.errorText.contains("Duplicate order")) {
                    logger.error { "Order ${order.hostOrderId}' for ${order.hostName} already exists, error text: ${synqError.errorText}" }
                    throw DuplicateResourceException("errorCode: ${synqError.errorCode}, errorText: ${synqError.errorText}", error)
                } else {
                    // Pass other errors along with just simple logging
                    logger.error { "Some other error occurred! Code: ${synqError.errorCode}, text: ${synqError.errorText}" }
                    Mono.error(error)
                }
            }.onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
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
                type = SynqOrderPayload.SynqOrderType.STANDARD
            )

        webClient
            .delete()
            .uri(URI.create("$baseUrl/orders/$owner/$synqOrderId"))
            .retrieve()
            .toEntity(SynqError::class.java)
            .timeout(timeoutProperties.storage)
            .doOnError {
                if (it is TimeoutException) {
                    logger.error { "Timed out while deleting order '$orderId' for $hostName in SynQ" }
                } else {
                    logger.error(it) { "Error while deleting order '$orderId' for $hostName in SynQ" }
                }
            }.onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .awaitSingle()
    }

    override fun isInStorage(location: AssociatedStorage): Boolean = location == AssociatedStorage.SYNQ

    override fun canHandleItem(item: Item): Boolean {
        if (item.preferredEnvironment == Environment.FRAGILE) return false

        // SynQ can handle both NONE and FREEZE environments, so this is not checked
        return when (item.itemCategory) {
            ItemCategory.PAPER -> true
            ItemCategory.FILM -> item.preferredEnvironment == Environment.FREEZE
            ItemCategory.BULK_ITEMS -> true
            else -> false
        }
    }
}
