package no.nb.mlt.wls.order.service

import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.core.data.synq.SynqError
import no.nb.mlt.wls.core.data.synq.SynqError.Companion.createServerError
import no.nb.mlt.wls.order.payloads.ApiOrderPayload
import no.nb.mlt.wls.order.payloads.SynqOrder
import no.nb.mlt.wls.order.payloads.SynqOrderPayload
import no.nb.mlt.wls.order.payloads.toOrder
import no.nb.mlt.wls.order.payloads.toSynqPayload
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerErrorException
import reactor.core.publisher.Mono
import java.net.URI

@Service
class SynqOrderService(
    @Autowired val webClient: WebClient
) {
    @Value("\${synq.path.base}")
    lateinit var baseUrl: String

    suspend fun createOrder(payload: SynqOrderPayload): ResponseEntity<SynqError> {
        val uri = URI.create("$baseUrl/orders/batch")

        // Wrap the order in the way SynQ likes it
        val orders = SynqOrder(listOf(payload))

        return webClient
            .post()
            .uri(uri)
            .body(BodyInserters.fromValue(orders))
            .retrieve()
            .toEntity(SynqError::class.java)
            .onErrorResume(WebClientResponseException::class.java) { error ->
                val synqError = error.getResponseBodyAs(SynqError::class.java) ?: throw createServerError(error)
                if (synqError.errorCode == 1037 || synqError.errorCode == 1029) {
                    throw ResponseStatusException(HttpStatus.NOT_FOUND, synqError.errorText)
                }
                if (synqError.errorText.contains("Duplicate order")) {
                    Mono.error(DuplicateOrderException(error))
                } else {
                    Mono.error(error)
                }
            }
            .onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .onErrorReturn(DuplicateOrderException::class.java, ResponseEntity.ok().build())
            .awaitSingle()
    }

    suspend fun updateOrder(payload: ApiOrderPayload): ResponseEntity<SynqError> {
        val uri = URI.create("$baseUrl/orders/batch")

        val orders = SynqOrder(listOf(payload.toOrder().toSynqPayload()))

        // TODO - Extend error handling
        return webClient
            .put()
            .uri(uri)
            .bodyValue(orders)
            .retrieve()
            .toEntity(SynqError::class.java)
            .onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .awaitSingle()
    }
}

class DuplicateOrderException(override val cause: Throwable) : ServerErrorException("Order already exists in SynQ", cause)
