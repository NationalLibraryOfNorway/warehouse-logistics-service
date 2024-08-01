package no.nb.mlt.wls.order.service

import no.nb.mlt.wls.core.data.synq.SynqError
import no.nb.mlt.wls.order.payloads.SynqOrder
import no.nb.mlt.wls.order.payloads.SynqOrderPayload
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ServerErrorException
import reactor.core.publisher.Mono
import java.net.URI

@Service
class SynqOrderService(
    @Autowired val webClient: WebClient
) {
    @Value("\${synq.path.base}")
    lateinit var baseUrl: String

    fun createOrder(payload: SynqOrderPayload): Mono<SynqError?> {
        val uri = URI.create("$baseUrl/orders/batch")

        // Wrap the order in the way SynQ likes it
        val orders = SynqOrder(listOf(payload))

        return webClient
            .post()
            .uri(uri)
            .body(BodyInserters.fromValue(orders))
            .retrieve()
            .bodyToMono(SynqError::class.java)
            .onErrorMap {
                if (it is HttpClientErrorException) {
                    val errorBody = it.getResponseBodyAs(SynqError::class.java)

                    throw ServerErrorException(
                        "Failed to create product in SynQ, the storage system responded with error code: " +
                            "'${errorBody?.errorCode ?: "NO ERROR CODE FOUND"}' " +
                            "and error text: " +
                            "'${errorBody?.errorText ?: "NO ERROR TEXT FOUND"}'",
                        it
                    )
                }
                it
            }
            .log()
    }
}
