package no.nb.mlt.wls.order.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.nb.mlt.wls.order.payloads.ApiOrderPayload
import no.nb.mlt.wls.order.service.OrderService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["", "/v1"])
@Tag(name = "Order Controller", description = "API for ordering products via Hermes WLS")
class OrderController(val orderService: OrderService) {
    @Operation(
        summary = "Creates an order for products from the storage system",
        description = """Creates an order for specified products to appropriate storage systems via Hermes WLS.
            Orders are automatically sent to the systems that own the respective product(s).
        """
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "Created order for specified products to appropriate systems"
        ),
        ApiResponse(
            responseCode = "400",
            description =
                """Order payload is invalid and was not created.
                An empty error message means the order already exists with the current ID.
                Otherwise, the error message contains information about the invalid fields.""",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "401",
            description = "Client sending the request is not authorized order products.",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "403",
            description = "A valid 'Authorization' header is missing from the request.",
            content = [Content(schema = Schema())]
        )
    )
    @PostMapping("/order/batch/create")
    suspend fun createOrder(
        @RequestBody payload: ApiOrderPayload
    ): ResponseEntity<ApiOrderPayload> = orderService.createOrder(payload)
}
