package no.nb.mlt.wls.application.hostapi.order

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.nb.mlt.wls.application.hostapi.ErrorMessage
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.inbound.CreateOrder
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import no.nb.mlt.wls.domain.ports.inbound.DeleteOrder
import no.nb.mlt.wls.domain.ports.inbound.GetOrder
import no.nb.mlt.wls.domain.ports.inbound.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.UpdateOrder
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping(path = [ "/v1"])
@Tag(name = "Order Controller", description = "API for ordering items via Hermes WLS")
class OrderController(
    private val createOrder: CreateOrder,
    private val getOrder: GetOrder,
    private val deleteOrder: DeleteOrder,
    private val updateOrder: UpdateOrder
) {
    @Operation(
        summary = "Creates an order for items from the storage system",
        description = """Creates an order for specified items to appropriate storage systems via Hermes WLS.
            Orders are automatically sent to the systems that own the respective items(s)."""
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = """Order with given 'hostName' and 'hostOrderId' already exists in the system.
                No new order was created, neither was the old order updated.
                Existing order information is returned for inspection.
                In rare cases the response body may be empty, that can happen if Hermes WLS does not
                have the information about the order stored in its database and is unable to retrieve
                the existing order information from the storage system.""",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiOrderPayload::class)
                )
            ]
        ),
        ApiResponse(
            responseCode = "201",
            description = "Created order for specified items to appropriate systems",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiOrderPayload::class)
                )
            ]
        ),
        ApiResponse(
            responseCode = "400",
            description = """Order payload is invalid and was not created.
                An empty error message means the order already exists with the current ID.
                Otherwise, the error message contains information about the invalid fields.""",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorMessage::class)
                )
            ]
        ),
        ApiResponse(
            responseCode = "401",
            description = "Client sending the request is not authorized to order items.",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "403",
            description = "A valid 'Authorization' header is missing from the request.",
            content = [Content(schema = Schema())]
        )
    )
    @PostMapping("/order")
    suspend fun createOrder(
        @AuthenticationPrincipal jwt: JwtAuthenticationToken,
        @RequestBody payload: ApiOrderPayload
    ): ResponseEntity<ApiOrderPayload> {
        throwIfInvalidClientName(jwt.name, payload.hostName)
        throwIfInvalid(payload)

        // Return 200 OK and existing order if it exists
        getOrder.getOrder(payload.hostName, payload.hostOrderId)?.let {
            return ResponseEntity
                .status(HttpStatus.OK)
                .body(it.toApiOrderPayload())
        }

        val createdOrder =
            createOrder.createOrder(
                CreateOrderDTO(
                    hostName = payload.hostName,
                    hostOrderId = payload.hostOrderId,
                    orderLine =
                        payload.orderLine.map {
                            CreateOrderDTO.OrderItem(
                                it.hostId
                            )
                        },
                    orderType = payload.orderType,
                    owner = payload.owner,
                    receiver = payload.receiver,
                    callbackUrl = payload.callbackUrl
                )
            )

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(createdOrder.toApiOrderPayload())
    }

    @Operation(
        summary = "Gets an order from the storage system",
        description = "Checks if a specified order exists within Hermes WLS."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Order with given 'hostName' and 'hostOrderId' exists in the system.",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiOrderPayload::class)
                )
            ]
        ),
        ApiResponse(
            responseCode = "400",
            description = """Some field was invalid in your request. The error message contains information about the invalid fields.""",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "401",
            description = "Client sending the request is not authorized to request orders, or this order does not belong to them.",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "403",
            description = "A valid 'Authorization' header is missing from the request.",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "404",
            description = "The order with hostname and hostOrderId does not exist in the system.",
            content = [Content(schema = Schema())]
        )
    )
    @GetMapping("/order/{hostName}/{hostOrderId}")
    suspend fun getOrder(
        @AuthenticationPrincipal jwt: JwtAuthenticationToken,
        @PathVariable("hostName") hostName: HostName,
        @PathVariable("hostOrderId") hostOrderId: String
    ): ResponseEntity<ApiOrderPayload> {
        throwIfInvalidClientName(jwt.name, hostName)

        val order = getOrder.getOrder(hostName, hostOrderId) ?: return ResponseEntity.notFound().build()

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(order.toApiOrderPayload())
    }

    @Operation(
        summary = "Updates an existing order in the storage system(s)",
        description = """Updates a specified order to the various storage systems via Hermes WLS.
        """
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "The order was updated with the new items, and sent to appropriate systems",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiOrderPayload::class)
                )
            ]
        ),
        ApiResponse(
            responseCode = "400",
            description = """Order payload is invalid and the order was not updated.
                This error is also produced if the order specified does not exist.
                Otherwise, the error message contains information about the invalid fields.""",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "401",
            description = "Client sending the request is not authorized to update orders, or this order does not belong to them.",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "403",
            description = "A valid 'Authorization' header is missing from the request.",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "409",
            description = "The order is already being processed, and can not be edited at this point.",
            content = [Content(schema = Schema())]
        )
    )
    @PutMapping("/order")
    suspend fun updateOrder(
        @AuthenticationPrincipal jwt: JwtAuthenticationToken,
        @RequestBody payload: ApiUpdateOrderPayload
    ): ResponseEntity<ApiOrderPayload> {
        throwIfInvalidClientName(jwt.name, payload.hostName)
        payload.throwIfInvalid()

        val updatedOrder =
            updateOrder.updateOrder(
                hostName = payload.hostName,
                hostOrderId = payload.hostOrderId,
                itemHostIds = payload.orderLine.map { it.hostId },
                orderType = payload.orderType,
                receiver = payload.receiver,
                callbackUrl = payload.callbackUrl
            )

        return ResponseEntity.ok(updatedOrder.toApiOrderPayload())
    }

    @Operation(
        summary = "Deletes an order from the storage system",
        description = """Deletes an order from the appropriate storage systems via Hermes WLS.
            To delete an order it must have a status of "NOT_STARTED".
            Additionally the caller must "own", e.g. be the creator of the order."""
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Order with given 'hostName' and 'hostOrderId' was deleted from the system.",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "401",
            description = "Client sending the request is not authorized to delete orders, or this order does not belong to them."
        ),
        ApiResponse(
            responseCode = "403",
            description = """A valid 'Authorization' header is missing from the request,
                    or the caller is not authorized to delete the order."""
        ),
        ApiResponse(
            responseCode = "404",
            description = "Order with given 'hostName' and 'hostOrderId' does not exist in the system."
        )
    )
    @DeleteMapping("/order/{hostName}/{hostOrderId}")
    suspend fun deleteOrder(
        @PathVariable hostName: HostName,
        @PathVariable hostOrderId: String,
        @AuthenticationPrincipal caller: JwtAuthenticationToken
    ): ResponseEntity<String> {
        if (hostOrderId.isBlank()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("The order's hostOrderId is required, and can not be blank")
        }
        throwIfInvalidClientName(caller.name, hostName)

        try {
            deleteOrder.deleteOrder(hostName, hostOrderId)
        } catch (e: OrderNotFoundException) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok().build()
    }

    fun throwIfInvalidClientName(
        clientName: String,
        hostName: HostName
    ) {
        if (clientName == "wls" || clientName.uppercase() == hostName.name) return
        throw ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "You do not have access to view resources owned by $hostName"
        )
    }
}
