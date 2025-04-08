package no.nb.mlt.wls.infrastructure.synq

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.domain.TimeoutProperties
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.outbound.DuplicateResourceException
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.net.URI
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
class SynqStandardAdapter(
    @Qualifier("nonProxyWebClient")
    private val webClient: WebClient,
    @Value("\${synq.path.base}")
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
            }
            .onErrorResume(WebClientResponseException::class.java) { error ->
                val errorText = error.getResponseBodyAs(SynqError::class.java)?.errorText
                if (errorText != null && errorText.contains("Duplicate product")) {
                    Mono.error(SynqError.DuplicateItemException(error))
                } else {
                    Mono.error(error)
                }
            }.onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .onErrorComplete(SynqError.DuplicateItemException::class.java)
            .awaitSingle()
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
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while creating order '${order.hostOrderId}' for ${order.hostName} in SynQ"
                }
            }
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
            }.onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .awaitSingle()
    }

    override suspend fun deleteOrder(
        orderId: String,
        hostName: HostName
    ) {
        // Special handling for Arkivverket is required for the storage systems
        val owner = toSynqOwner(hostName)

        webClient
            .delete()
            .uri(URI.create("$baseUrl/orders/$owner/$orderId"))
            .retrieve()
            .toEntity(SynqError::class.java)
            .timeout(timeoutProperties.storage)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while deleting order '$orderId' for $hostName in SynQ"
                }
            }
            .onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .awaitSingle()
    }

    override suspend fun updateOrder(order: Order): Order =
        webClient
            .put()
            .uri(URI.create("$baseUrl/orders/batch"))
            .bodyValue(SynqOrder(listOf(order.toSynqStandardPayload())))
            .retrieve()
            .toBodilessEntity()
            .timeout(timeoutProperties.storage)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while updating order '${order.hostOrderId}' for ${order.hostName} in SynQ"
                }
            }
            .map { order }
            .onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .awaitSingle()

    override suspend fun canHandleLocation(location: String): Boolean =
        when (location.uppercase()) {
            "SYNQ_WAREHOUSE" -> true
            else -> false
        }

    override fun canHandleItem(item: Item): Boolean {
        // SynQ can handle both NONE and FREEZE environments, so this is not checked
        return when (item.itemCategory) {
            ItemCategory.PAPER -> true
            ItemCategory.FILM -> true
            ItemCategory.BULK_ITEMS -> false
            ItemCategory.FRAGILE -> false
            else -> false
        }
    }
}
