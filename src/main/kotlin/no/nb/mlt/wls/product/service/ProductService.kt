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

@Service
class ProductService(val db: ProductRepository, val synqService: SynqService) {
    fun exists(product: Product): Boolean {
        return db.existsByHostId(product.hostId)
    }

    fun save(payload: ApiProductPayload): ResponseEntity<ApiProductPayload> {
        // Product service should validate the product, and return a 400 response if it is invalid
        val invalidResponse = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(payload)

        // REVIEW - Everything validated?
        // And do we need more descriptive error messages?
        if (payload.description.isBlank()) {
            return invalidResponse
        }

        if (payload.productCategory.isBlank()) {
            return invalidResponse
        }

        // Quantity has to be a whole number, despite some storage systems supporting floating points
        if (payload.quantity == null || Math.floor(payload.quantity) != payload.quantity) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(payload)
        }

        // Product service should check if product already exists, and return a 200 response if it does
        val queriedProducts = getByHostNameAndId(payload.hostName, payload.hostId)
        if (queriedProducts != null) {
            return ResponseEntity.ok(payload)
        }
        // TODO - Should we roll back if SynQ fails to create the product?

        // Product service should save the product in DB and appropriate storage system, and return a 201 response
        val product = db.save(payload.toProduct())
        synqService.createProduct(product.toSynqPayload())

        return ResponseEntity.status(HttpStatus.CREATED).body(product.toApiPayload())
    }

    fun getByHostNameAndId(
        hostName: HostName,
        name: String
    ): Product? {
        return db.findByHostNameAndHostId(hostName, name)
    }
}
