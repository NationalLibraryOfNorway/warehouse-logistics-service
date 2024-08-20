package no.nb.mlt.wls.product.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import no.nb.mlt.wls.product.payloads.ApiProductPayload
import no.nb.mlt.wls.product.payloads.toApiPayload
import no.nb.mlt.wls.product.payloads.toProduct
import no.nb.mlt.wls.product.payloads.toSynqPayload
import no.nb.mlt.wls.product.repository.ProductRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.server.ServerErrorException
import org.springframework.web.server.ServerWebInputException
import java.time.Duration
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Service
class ProductService(val db: ProductRepository, val synqProductService: SynqProductService) {
    suspend fun save(payload: ApiProductPayload): ResponseEntity<ApiProductPayload> {
        // Check if the payload is valid and throw an exception if it is not
        throwIfInvalidPayload(payload)

        // TODO - See if timeouts can be made configurable
        // Product service should check if product already exists, and return a 200 response if it does
        val existingProduct =
            db.findByHostNameAndHostId(payload.hostName, payload.hostId)
                .timeout(Duration.ofSeconds(8))
                .onErrorMap(TimeoutException::class.java) {
                    logger.error(it) {
                        "Timed out while fetching from WLS database. Payload: $payload"
                    }
                    ServerErrorException("Failed to save product in the database", it)
                }
                .awaitSingleOrNull()

        if (existingProduct != null) {
            return ResponseEntity.ok(existingProduct.toApiPayload())
        }

        // Convert payload to product, but set quantity and location to default values in case they are given
        // We don't want to use values from the payload, as the product should not be in the storage system yet
        // Hence its quantity should be 0 and location should be null
        // Not done in the mapping function as we want it to be explicit for clarity
        val product = payload.toProduct().copy(quantity = 0.0, location = null)

        // Product service should create the product in the storage system, and return error message if it fails
        val synqResponse = synqProductService.createProduct(product.toSynqPayload())
        // If SynQ didn't throw an error, but returned 4xx/5xx, then it is likely some error or edge-case we haven't handled
        if (synqResponse.statusCode.is4xxClientError || synqResponse.statusCode.is5xxServerError) {
            throw ServerErrorException("Unexpected error with SynQ", null)
        }
        // If SynQ returned a 200 OK then it means it exists from before, and we can return empty response (since we don't have any order info)
        if (synqResponse.statusCode.isSameCodeAs(HttpStatus.OK)) {
            return ResponseEntity.ok().build()
        }

        // Product service should save the product in the database, and return 500 if it fails
        db.save(product)
            .timeout(Duration.ofSeconds(6))
            .onErrorMap {
                ServerErrorException("Failed to save product in the database, but created in the storage system", it)
            }
            .awaitSingle()

        // Product service should return a 201 response if the product was created with created product in response body
        return ResponseEntity.status(HttpStatus.CREATED).body(product.toApiPayload())
    }

    private fun throwIfInvalidPayload(payload: ApiProductPayload) {
        if (payload.hostId.isBlank()) {
            throw ServerWebInputException("The product's hostId is required, and it cannot be blank")
        }

        if (payload.description.isBlank()) {
            throw ServerWebInputException("The product's description is required, and it cannot be blank")
        }

        if (payload.productCategory.isBlank()) {
            throw ServerWebInputException("The product's category is required, and it cannot be blank")
        }
    }
}
