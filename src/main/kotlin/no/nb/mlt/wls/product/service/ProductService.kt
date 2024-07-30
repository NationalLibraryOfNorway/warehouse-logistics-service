package no.nb.mlt.wls.product.service

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.product.model.Product
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
import kotlin.math.ceil
import kotlin.math.floor

@Service
class ProductService(val db: ProductRepository, val synqProductService: SynqProductService) {
    fun save(payload: ApiProductPayload): ResponseEntity<ApiProductPayload> {
        // Check if the payload is valid and throw an exception if it is not
        throwIfInvalidPayload(payload)

        // Product service should check if product already exists, and return a 200 response if it does
        val existingProduct = getByHostNameAndId(payload.hostName, payload.hostId)
        if (existingProduct != null) {
            return ResponseEntity.ok(existingProduct.toApiPayload())
        }

        // Convert payload to product, but set quantity and location to default values in case they are given
        // We don't want to use values from the payload, as the product should not be in the storage system yet
        // Hence its quantity should be 0 and location should be null
        // Not done in the mapping function as we want it to be explicit for clarity
        val product = payload.toProduct().copy(quantity = 0.0, location = null)

        // Product service should create the product in the storage system, and return error message if it fails
        if (synqProductService.createProduct(product.toSynqPayload()).statusCode.isSameCodeAs(HttpStatus.OK)) {
            return ResponseEntity.ok().build()
        }

        // Product service should save the product in the database, and return 500 if it fails
        try {
            db.save(product)
        } catch (e: Exception) {
            throw ServerErrorException("Failed to save product in the database, but created in the storage system", e)
        }

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

    fun getByHostNameAndId(
        hostName: HostName,
        name: String
    ): Product? {
        return db.findByHostNameAndHostId(hostName, name)
    }
}
