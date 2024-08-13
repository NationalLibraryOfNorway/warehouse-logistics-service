package no.nb.mlt.wls.product.service

import no.nb.mlt.wls.core.data.synq.SynqError
import no.nb.mlt.wls.product.payloads.SynqProductPayload
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
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
        try {
            return ResponseEntity(
                webClient
                    .post()
                    .uri(uri)
                    .body(BodyInserters.fromValue(payload))
                    .retrieve()
                    .bodyToMono(SynqError::class.java)
                    .block(),
                HttpStatus.CREATED
            )
        } catch (exception: HttpClientErrorException) {
            // Get SynQ error from the response body if possible
            val errorBody = exception.getResponseBodyAs(SynqError::class.java)

            // SynQ will return an error if there is a duplicate, which is ok
            if (errorBody != null && errorBody.errorText.contains("Duplicate product")) {
                // If we got here that means we don't have product info in our database
                // Since there's no way of getting product info from SynQ we cannot return existing product in the body
                // Hence in this case we will break API contract, however this seems unlikely that we will get here, so we can live with it
                // Alternatively we could convert provided SynqProductPayload to ApiProductPayload and return that
                // However that could be wrong if SynQ has different data than what we got from the client
                // For now we will just return 200 OK with an empty body
                return ResponseEntity.ok().build()
            }

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
