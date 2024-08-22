package no.nb.mlt.wls.order.service

import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.synq.SynqError
import no.nb.mlt.wls.core.data.synq.SynqError.Companion.createServerError
import no.nb.mlt.wls.order.payloads.SynqOrder
import no.nb.mlt.wls.order.payloads.SynqOrderPayload
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
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
                val errorText = error.getResponseBodyAs(SynqError::class.java)?.errorText
                if (errorText != null && errorText.contains("Duplicate order")) {
                    Mono.error(DuplicateOrderException(error))
                } else {
                    Mono.error(error)
                }
            }
            .onErrorMap(WebClientResponseException::class.java) { createServerError(it) }
            .onErrorReturn(DuplicateOrderException::class.java, ResponseEntity.ok().build())
            .awaitSingle()
    }

    suspend fun deleteOrder(
        hostName: HostName,
        hostOrderId: String
    ): ResponseEntity<SynqError> {
        val uri = URI.create("$baseUrl/orders/$hostName/$hostOrderId")

        return webClient
            .delete()
            .uri(uri)
            .retrieve()
            .toEntity(SynqError::class.java)
            .onErrorMap(WebClientResponseException::class.java) { createServerError(it) } // TODO: FIXME TEXT IS WRONG!!!
            .awaitSingle()
    }
}

class DuplicateOrderException(override val cause: Throwable) : ServerErrorException("Order already exists in SynQ", cause)
