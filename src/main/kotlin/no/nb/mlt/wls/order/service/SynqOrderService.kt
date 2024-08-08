package no.nb.mlt.wls.order.service

import no.nb.mlt.wls.core.data.synq.SynqError
import no.nb.mlt.wls.order.payloads.SynqOrder
import no.nb.mlt.wls.order.payloads.SynqOrderPayload
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.server.ServerErrorException
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

        try {
            return ResponseEntity(
                webClient
                    .post()
                    .uri(uri)
                    .body(BodyInserters.fromValue(orders))
                    .retrieve()
                    .awaitBody<SynqError>(),
                HttpStatus.CREATED
            )
        } catch (exception: HttpClientErrorException) {
            val errorBody = exception.getResponseBodyAs(SynqError::class.java)

            throw ServerErrorException(
                "Failed to create product in SynQ, the storage system responded with error code: " +
                    "'${errorBody?.errorCode ?: "NO ERROR CODE FOUND"}' " +
                    "and error text: " +
                    "'${errorBody?.errorText ?: "NO ERROR TEXT FOUND"}'",
                exception
            )
        }
    }
}
