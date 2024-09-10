package no.nb.mlt.wls.infrastructure.synq

import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.ports.inbound.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import no.nb.mlt.wls.infrastructure.synq.SynqError.Companion.createServerError
import no.nb.mlt.wls.infrastructure.synq.SynqProductPayload.SynqPackaging
import no.nb.mlt.wls.infrastructure.synq.SynqProductPayload.SynqPackaging.ESK
import no.nb.mlt.wls.infrastructure.synq.SynqProductPayload.SynqPackaging.OBJ
import no.nb.mlt.wls.order.payloads.SynqOrder
import no.nb.mlt.wls.order.payloads.toSynqPayload
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
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
            .body(BodyInserters.fromValue(item.toSynqPayload()))
            .retrieve()
            .toEntity(SynqError::class.java)
            .onErrorResume(WebClientResponseException::class.java) { error ->
                val errorText = error.getResponseBodyAs(SynqError::class.java)?.errorText
                if (errorText != null && errorText.contains("Duplicate product")) {
                    Mono.error(SynqError.DuplicateProductException(error))
                } else {
                    Mono.error(error)
                }
            }
            .onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .onErrorComplete(SynqError.DuplicateProductException::class.java)
            .awaitSingle()
    }

    override suspend fun createOrder(order: Order) {
        // Wrap the order in the way SynQ likes it
        val orders = SynqOrder(listOf(order.toSynqPayload()))

        webClient
            .post()
            .uri(URI.create("$baseUrl/orders/batch"))
            .body(BodyInserters.fromValue(orders))
            .retrieve()
            .toEntity(SynqError::class.java)
            .onErrorResume(WebClientResponseException::class.java) { error ->
                val synqError = error.getResponseBodyAs(SynqError::class.java) ?: throw createServerError(error)
                if (synqError.errorCode == 1037 || synqError.errorCode == 1029) {
                    throw OrderNotFoundException(synqError.errorText)
                }
                if (synqError.errorText.contains("Duplicate order")) {
                    Mono.error(SynqError.DuplicateOrderException(error))
                } else {
                    Mono.error(error)
                }
            }
            .onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .onErrorReturn(SynqError.DuplicateOrderException::class.java, ResponseEntity.ok().build())
            .awaitSingle()
    }
}

fun Item.toSynqPayload() =
    SynqProductPayload(
        productId = hostId,
        owner = owner.toSynqOwner(),
        barcode = SynqProductPayload.Barcode(hostId),
        description = description,
        productCategory = productCategory,
        productUom = SynqProductPayload.ProductUom(packaging.toSynqPackaging()),
        confidential = false,
        hostName = hostName.toString()
    )

fun Packaging.toSynqPackaging(): SynqPackaging =
    when (this) {
        Packaging.NONE -> OBJ
        Packaging.BOX -> ESK
    }
