package no.nb.mlt.wls.product.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import no.nb.mlt.wls.product.payloads.ApiProductPayload
import no.nb.mlt.wls.product.payloads.toProduct
import no.nb.mlt.wls.product.payloads.toSynqPayload
import no.nb.mlt.wls.product.service.ProductService
import no.nb.mlt.wls.product.service.SynqService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Product Controller", description = "Product API")
@RestController
@RequestMapping("/v1")
class ProductController(val synqService: SynqService, val productService: ProductService) {
    @Operation(
        summary = "Register a product to the storage system",
        description = "Sends a payload with metadata for registering a product into Hermes WLS. This will also register the product to SynQ"
    )
    @PostMapping("/product")
    fun createProduct(
        authentication: Authentication,
        @RequestBody payload: ApiProductPayload
    ) {
        val product = payload.toProduct()
        productService.save(product)
        synqService.createProduct(product.toSynqPayload())
    }
}
