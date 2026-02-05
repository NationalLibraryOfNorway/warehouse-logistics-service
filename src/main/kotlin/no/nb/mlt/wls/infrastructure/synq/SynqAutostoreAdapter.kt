package no.nb.mlt.wls.infrastructure.synq

import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import org.springframework.stereotype.Component

@Component
class SynqAutostoreAdapter(
    private val synqAdapter: SynqAdapter
) : StorageSystemFacade {
    override suspend fun createItem(item: Item) {
        synqAdapter.createItem(item.toSynqPayload())
    }

    override suspend fun editItem(item: Item) {
        val product = item.toSynqPayload()
        val uri = URI.create("$baseUrl/nbproducts/${product.owner}/${product.productId}")

        webClient
            .put()
            .uri(uri)
            .bodyValue(product)
            .retrieve()
            .toEntity<SynqError>()
            .timeout(timeoutProperties.storage)
            .doOnError(TimeoutException::class.java) {
                logger.error { "Timed out while editing item '${item.hostId}' for ${item.hostName} in SynQ" }
            }.onErrorMap(WebClientResponseException::class.java) { error ->
                if (error.statusCode.isSameCodeAs(HttpStatus.NOT_FOUND)) {
                    ResourceNotFoundException(error.message, error)
                } else {
                    createServerError(error)
                }
            }.awaitFirst()
    }

    override suspend fun createOrder(order: Order) {
        synqAdapter.createOrder(order.toAutostorePayload())
    }

    override suspend fun deleteOrder(
        orderId: String,
        hostName: HostName
    ) {
        val owner = toSynqOwner(hostName)
        val synqOrderId =
            computeOrderId(
                hostName = hostName,
                hostOrderId = orderId,
                type = SynqOrderPayload.SynqOrderType.AUTOSTORE
            )
        synqAdapter.deleteOrder(owner, synqOrderId)
    }

    override fun isInStorage(location: AssociatedStorage): Boolean = location == AssociatedStorage.AUTOSTORE

    // This adapter method is a no-op, since the item creation is handled by the standard SynQ adapter
    override fun canHandleItem(item: Item): Boolean = false

    override fun getName(): String = "AutoStore"
}
