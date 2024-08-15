package no.nb.mlt.wls.order.service

import kotlinx.coroutines.reactor.awaitSingle
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
            .onErrorMap(WebClientResponseException::class.java) {
                createServerError(it)
            }
            .awaitSingle()
    }
}
