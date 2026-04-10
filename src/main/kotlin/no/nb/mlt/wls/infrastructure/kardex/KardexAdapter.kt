package no.nb.mlt.wls.infrastructure.kardex

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import no.nb.mlt.wls.domain.ports.outbound.exceptions.StorageSystemException
import no.nb.mlt.wls.infrastructure.config.TimeoutProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntity
import org.springframework.web.util.UriComponentsBuilder
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
@ConditionalOnBooleanProperty(
    prefix = "kardex",
    value = ["enabled"],
    havingValue = true,
    matchIfMissing = true
)
class KardexAdapter(
    @param:Qualifier("nonProxyWebClient")
    private val webClient: WebClient,
    @param:Value($$"${kardex.path.base}")
    private val baseUrl: String,
    private val timeoutProperties: TimeoutProperties
) : StorageSystemFacade {
    override suspend fun createItem(item: Item) {
        webClient
            .post()
            .uri(UriComponentsBuilder.fromUriString(baseUrl).pathSegment("materials").build().toUri())
            .bodyValue(item.toKardexPayload())
            .retrieve()
            .toEntity<KardexResponse>()
            .timeout(timeoutProperties.storage)
            .handle { it, sink ->
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

    override suspend fun editItem(item: Item) {
        val uri = UriComponentsBuilder.fromUriString(baseUrl)
            .pathSegment("materials", "{material}")
            .queryParam("material", "{material}")
            .queryParam("materialRequest", "{body}")
            .buildAndExpand(item.hostId, item.hostId, item.toKardexPayload())
            .toUri()

        webClient
            .put()
            .uri(uri)
            .retrieve()
            .toEntity<KardexResponse>()
            .timeout(timeoutProperties.storage)
            .handle { it, sink ->
                if (it.body?.isError() == true) {
                    sink.error(StorageSystemException("Failed to edit item in Kardex: ${it.body?.message}"))
                    it.body!!.errors.forEach {
                        logger.error { "${it.item}: ${it.errors}" }
                    }
                } else {
                    sink.next(it)
                }
            }.doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while editing item '${item.hostId}' for ${item.hostName} in Kardex"
                }
            }.awaitSingle()
    }

    override suspend fun createOrder(order: Order) {
        webClient
            .post()
            .uri(UriComponentsBuilder.fromUriString(baseUrl).pathSegment("orders").build().toUri())
            .bodyValue(order.toKardexOrderPayload())
            .retrieve()
            .toEntity<KardexResponse>()
            .timeout(timeoutProperties.storage)
            .handle { it, sink ->
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
        webClient
            .delete()
            .uri(UriComponentsBuilder.fromUriString(baseUrl).pathSegment("orders", "{orderId}").buildAndExpand(orderId).toUri())
            .retrieve()
            .toEntity<String>()
            .timeout(timeoutProperties.storage)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while deleting order '$orderId' for $hostName in Kardex"
                }
            }.awaitSingle()
    }

    override fun isInStorage(location: AssociatedStorage): Boolean = location == AssociatedStorage.KARDEX

    override fun canHandleItem(item: Item): Boolean {
        if (item.hostName == HostName.AXIELL) {
            if (item.preferredEnvironment == Environment.FRAGILE) {
                return false
            }
            return when (item.itemCategory) {
                ItemCategory.FILM, ItemCategory.PAPER, ItemCategory.MAGNETIC_TAPE -> true
                else -> false
            }
        }
        return false
    }

    override fun getName(): String = "Kardex"
}
