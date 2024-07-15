package no.nb.mlt.wls.product.service

import no.nb.mlt.wls.product.payloads.SynqProductPayload
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.net.URI

@Service
class SynqService {
    val restTemplate: RestTemplate = RestTemplate()

    @Value("\${synq.path.base}")
    lateinit var baseUrl: String

    fun createProduct(payload: SynqProductPayload): ResponseEntity<String> {
        // NOTE - Could trust validation from product service? Or should this have some SynQ specific validation?
        val uri = URI.create("$baseUrl/nbproducts")
        try {
            val response = restTemplate.exchange(uri, HttpMethod.POST, HttpEntity(payload), String::class.java)
            return response
        } catch (e: HttpClientErrorException) {
            // SynQ will return an error if there is a duplicate, which is ok
            if (e.message?.contains("duplicate") == true) {
                return ResponseEntity.ok("")
            }

            return ResponseEntity(e.message, e.statusCode)
        }
    }
}
