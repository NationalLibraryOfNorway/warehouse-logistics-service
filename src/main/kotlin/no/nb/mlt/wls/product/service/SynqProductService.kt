package no.nb.mlt.wls.product.service

import no.nb.mlt.wls.core.data.synq.SynqError
import no.nb.mlt.wls.product.exceptions.DuplicateProductException
import no.nb.mlt.wls.product.payloads.SynqProductPayload
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ServerErrorException
import java.net.URI

@Service
class SynqProductService(
    @Autowired val webClient: WebClient
) {
    @Value("\${synq.path.base}")
    lateinit var baseUrl: String

    fun createProduct(payload: SynqProductPayload): ResponseEntity<SynqError> {
        // NOTE - Could trust validation from product service? Or should this have some SynQ specific validation?
        val uri = URI.create("$baseUrl/nbproducts")
        return webClient
            .post()
            .uri(uri)
            .body(BodyInserters.fromValue(payload))
            .retrieve()
            .toEntity(SynqError::class.java)
            .onErrorMap {
                if (it is HttpClientErrorException) {
                    val errorBody = it.getResponseBodyAs(SynqError::class.java)
                    if (errorBody != null && errorBody.errorText.contains("Duplicate product")) {
                        DuplicateProductException()
                    }
                    transformSynqError(it)
                }
                it
            }
            .onErrorMap(WebClientResponseException::class.java, { transformSynqError(it) })
            .onErrorReturn(DuplicateProductException::class.java, ResponseEntity.ok().build())
            .block()!!
    }

    fun transformSynqError(t: Throwable): ServerErrorException {
        var errorBody: SynqError? = null

        if (t is WebClientResponseException) {
            errorBody = t.getResponseBodyAs(SynqError::class.java)
        }

        if (t is WebClientResponseException) {
            errorBody = t.getResponseBodyAs(SynqError::class.java)
        }

        return ServerErrorException(
            "Failed to create product in SynQ, the storage system responded with error code: " +
                "'${errorBody?.errorCode ?: "NO ERROR CODE FOUND"}' " +
                "and error text: " +
                "'${errorBody?.errorText ?: "NO ERROR TEXT FOUND"}'",
            t
        )
    }
}
