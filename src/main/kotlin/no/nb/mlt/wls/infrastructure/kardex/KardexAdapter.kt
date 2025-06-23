package no.nb.mlt.wls.infrastructure.kardex

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.domain.TimeoutProperties
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemException
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.net.URI
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
@ConditionalOnProperty(value = ["kardex.enabled"], havingValue = "true")
class KardexAdapter(
    @Qualifier("nonProxyWebClient")
    private val webClient: WebClient,
    @Value("\${kardex.path.base}")
    private val baseUrl: String,
    private val timeoutProperties: TimeoutProperties
) : StorageSystemFacade {
    override suspend fun createItem(item: Item) {
        val uri = URI.create("$baseUrl/materials")

        webClient
            .post()
            .uri(uri)
            .bodyValue(item.toKardexPayload())
            .retrieve()
            .toEntity(KardexResponse::class.java)
            .timeout(timeoutProperties.storage)
            .handle<ResponseEntity<KardexResponse>> { it, sink ->
                if (it.body?.isError() == true) {
                    sink.error(StorageSystemException("Failed to create item in Kardex: ${it.body?.message}"))
                    it.body!!.errors.forEach {
                        logger.error { "${it.item}: ${it.errors}" }
                    }
                } else {
                    sink.next(it)
                }
            }.doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while creating item '${item.hostId}' for ${item.hostName} in Kardex"
                }
            }.awaitSingle()
    }

    override suspend fun createOrder(order: Order) {
        val uri = URI.create("$baseUrl/orders")

        webClient
            .post()
            .uri(uri)
            .bodyValue(order.toKardexOrderPayload())
            .retrieve()
            .toEntity(KardexResponse::class.java)
            .timeout(timeoutProperties.storage)
            .handle<ResponseEntity<KardexResponse>> { it, sink ->
                if (it.body?.isError() == true) {
                    it.body!!.errors.forEach {
                        logger.error { "${it.item}: ${it.errors}" }
                    }
                    sink.error(StorageSystemException("Failed to create order in Kardex: ${it.body?.message}"))
                } else {
                    sink.next(it)
                }
            }.doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while creating order '${order.hostOrderId}' for ${order.hostName} in Kardex"
                }
            }.awaitSingle()
    }

    override suspend fun deleteOrder(
        orderId: String,
        hostName: HostName
    ) {
        val uri = URI.create("$baseUrl/orders/$orderId")

        webClient
            .delete()
            .uri(uri)
            .retrieve()
            .toEntity(String::class.java)
            .timeout(timeoutProperties.storage)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while deleting order '$orderId' for $hostName in Kardex"
                }
            }.awaitSingle()
    }

    override suspend fun canHandleLocation(location: String): Boolean = location == "NB Mo i Rana"

    override fun canHandleItem(item: Item) = item.preferredEnvironment != Environment.FRAGILE
}
