package no.nb.mlt.wls.product.service

import kotlinx.coroutines.reactor.awaitSingle
import no.nb.mlt.wls.core.data.synq.SynqError
import no.nb.mlt.wls.product.payloads.SynqProductPayload
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
class SynqProductService(
    @Autowired val webClient: WebClient
) {
    @Value("\${synq.path.base}")
    lateinit var baseUrl: String

    suspend fun createProduct(payload: SynqProductPayload): ResponseEntity<SynqError> {
        // NOTE - Could trust validation from product service? Or should this have some SynQ specific validation?
        val uri = URI.create("$baseUrl/nbproducts")
        return webClient
            .post()
            .uri(uri)
            .body(BodyInserters.fromValue(payload))
            .retrieve()
            .toEntity(SynqError::class.java)
            .onErrorResume(WebClientResponseException::class.java) { error ->
                val errorText = error.getResponseBodyAs(SynqError::class.java)?.errorText
                if (errorText != null && errorText.contains("Duplicate product")) {
                    Mono.error(DuplicateProductException(error))
                } else {
                    Mono.error(error)
                }
            }
            .onErrorMap(WebClientResponseException::class.java) { transformSynqError(it) }
            .onErrorReturn(DuplicateProductException::class.java, ResponseEntity.ok().build())
            .awaitSingle()
    }

    fun transformSynqError(error: WebClientResponseException): ServerErrorException {
        val errorBody = error.getResponseBodyAs(SynqError::class.java)

        return ServerErrorException(
            "Failed to create product in SynQ, the storage system responded with error code: " +
                "'${errorBody?.errorCode ?: "NO ERROR CODE FOUND"}' " +
                "and error text: " +
                "'${errorBody?.errorText ?: "NO ERROR TEXT FOUND"}'",
            error
        )
    }
}

class DuplicateProductException(override val cause: Throwable) : ServerErrorException("Product already exists in SynQ", cause)
