package no.nb.mlt.wls.infrastructure.synq

import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import no.nb.mlt.wls.infrastructure.synq.SynqError.Companion.createServerError
import no.nb.mlt.wls.infrastructure.synq.SynqProductPayload.SynqPackaging
import no.nb.mlt.wls.infrastructure.synq.SynqProductPayload.SynqPackaging.ESK
import no.nb.mlt.wls.infrastructure.synq.SynqProductPayload.SynqPackaging.OBJ
import org.springframework.beans.factory.annotation.Value
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
