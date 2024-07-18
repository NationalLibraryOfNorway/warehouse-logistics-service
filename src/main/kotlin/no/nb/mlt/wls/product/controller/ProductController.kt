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
@RequestMapping(path =  ["", "/v1"])
@Tag(name = "Product Controller", description = "API for managing products in Hermes WLS")
class ProductController(val productService: ProductService) {
    @Operation(
        summary = "Register a product in the storage system",
        description =
            "Register data about the product in Hermes WLS and appropriate storage system, " +
                "so that the physical product can be placed in the physical storage."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description =
                    "Product with given 'hostName' and 'hostId' already exists in the system. " +
                        "No new product was created, neither was the old product updated. " +
                        "Existing product information is returned for inspection. " +
                        "In rare cases the response body may be empty, that can happen if Hermes WLS " +
                        "does not have the information about product stored in its database and is " +
                        "unable to retrieve the existing product information from the storage system.",
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
                    "Product payload is valid and the product information was registered successfully. " +
                        "Product was created in the appropriate storage system. " +
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
                    "Product payload is invalid and no new product was created. " +
                        "Error message contains information about the invalid fields.",
                content = [Content(schema = Schema())]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Client sending the request is not authorized to create a new product.",
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
