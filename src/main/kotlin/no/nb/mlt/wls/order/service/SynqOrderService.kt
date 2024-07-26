package no.nb.mlt.wls.order.service

import no.nb.mlt.wls.core.data.synq.SynqError
import no.nb.mlt.wls.order.payloads.SynqOrder
import no.nb.mlt.wls.order.payloads.SynqOrderPayload
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ServerErrorException
import java.net.URI

@Service
class SynqOrderService {
    val restTemplate: RestTemplate = RestTemplate()

    @Value("\${synq.path.base}")
    lateinit var baseUrl: String

    fun createOrder(payload: SynqOrderPayload): ResponseEntity<SynqError> {
        val uri = URI.create("$baseUrl/orders/batch")

        // Wrap the order in the way SynQ likes it
        val orders = SynqOrder(listOf(payload))

        try {
            return restTemplate.exchange(uri, HttpMethod.POST, HttpEntity(orders), SynqError::class.java)
        } catch (e: HttpClientErrorException) {
            val errorBody = e.getResponseBodyAs(SynqError::class.java)

            throw ServerErrorException(
                "Failed to create product in SynQ, the storage system responded with error code: " +
                    "'${errorBody?.errorCode ?: "NO ERROR CODE FOUND"}' " +
                    "and error text: " +
                    "'${errorBody?.errorText ?: "NO ERROR TEXT FOUND"}'",
                e
            )
        }
    }
}
