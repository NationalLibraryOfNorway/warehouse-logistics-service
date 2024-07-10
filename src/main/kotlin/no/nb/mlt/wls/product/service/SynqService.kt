package no.nb.mlt.wls.product.service

import no.nb.mlt.wls.product.payloads.SynqProductPayload
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URI

@Service
class SynqService {
    val restTemplate: RestTemplate = RestTemplate()

    @Value("\${synq.path.base}")
    lateinit var baseUrl: String

    fun createProduct(payload: SynqProductPayload) {
        // TODO - Validation
        val uri = URI.create("$baseUrl/nbproducts")
        val response = restTemplate.exchange(uri, HttpMethod.POST, HttpEntity(payload), String::class.java)
        println(response.statusCode)
    }
}