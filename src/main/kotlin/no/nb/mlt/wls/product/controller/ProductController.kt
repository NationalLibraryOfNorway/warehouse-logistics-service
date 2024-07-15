package no.nb.mlt.wls.product.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.nb.mlt.wls.product.payloads.ApiProductPayload
import no.nb.mlt.wls.product.service.ProductService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1")
@Tag(name = "Product Controller", description = "API for managing products in Hermes WLS")
class ProductController(val productService: ProductService) {
    @Operation(
        summary = "Register a product in the storage system",
        description =
            "Register data about the product in Hermes WLS and appropriate storage system, " +
                "so that the product can be inserted into the physical storage."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description =
                    "Product with given 'hostName' and 'hostId' already exists in the system. " +
                        "Product was not created or updated. " +
                        "Existing product information is returned for inspection.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiProductPayload::class)
                    )
                ]
            ),
            ApiResponse(
                responseCode = "201",
                description =
                    "Product payload is valid and product was registered successfully. " +
                        "New product information is returned for inspection.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiProductPayload::class)
                    )
                ]
            ),
            ApiResponse(
                responseCode = "400",
                description =
                    "Product payload is invalid and new product was not created. " +
                        "Error message contains information about the invalid fields.",
                content = [Content(schema = Schema())]
            ),
            ApiResponse(
                responseCode = "401",
                description = "This user is not authorized to create a product.",
                content = [Content(schema = Schema())]
            ),
            ApiResponse(
                responseCode = "403",
                description = "A valid 'Authorization' header is missing from the request.",
                content = [Content(schema = Schema())]
            )
        ]
    )
    @PostMapping("/product")
    fun createProduct(
        @RequestBody payload: ApiProductPayload
    ): ResponseEntity<ApiProductPayload> = productService.save(payload)
}
