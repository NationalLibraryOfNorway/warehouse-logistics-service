package no.nb.mlt.wls.application.hostapi.order

import io.micrometer.core.instrument.config.validate.Validated.valid
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.callbacks.Callback
import io.swagger.v3.oas.annotations.callbacks.Callbacks
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.nb.mlt.wls.application.hostapi.ErrorMessage
import no.nb.mlt.wls.application.hostapi.config.checkIfAuthorized
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.ports.inbound.CreateOrder
import no.nb.mlt.wls.domain.ports.inbound.DeleteOrder
import no.nb.mlt.wls.domain.ports.inbound.GetOrder
import no.nb.mlt.wls.domain.ports.inbound.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.UpdateOrder
import no.nb.mlt.wls.infrastructure.callbacks.NotificationOrderPayload
import org.bouncycastle.pqc.legacy.math.linearalgebra.IntegerFunctions.order
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
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping(path = ["/v1"])
@Tag(name = "Order Controller", description = """API for ordering items via Hermes WLS""")
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
            description = """Order with given "hostName" and "hostOrderId" already exists in the system.
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
            description = """Created order for specified items to appropriate systems""",
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
            description = """Client sending the request is not authorized to order items.""",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "403",
            description = """A valid "Authorization" header is missing from the request.""",
            content = [Content(schema = Schema())]
        )
    )
    @Callbacks(
        Callback(
            name = "Order Callback",
            callbackUrlExpression = "\$request.body#/callbackUrl",
            operation =
                arrayOf(
                    Operation(
                        summary = "Notification of updated order",
                        description = """This callback triggers when the order is updated inside the storage systems.
                            It returns the same data as one would receive from the GET endpoint, meaning complete information about the order.""",
                        method = "post",
                        requestBody =
                            SwaggerRequestBody(
                                content =
                                    arrayOf(
                                        Content(schema = Schema(implementation = NotificationOrderPayload::class))
                                    ),
                                required = true
                            )
                    )
                )
        )
    )
    @PostMapping("/order")
    suspend fun createOrder(
        @AuthenticationPrincipal jwt: JwtAuthenticationToken,
        @RequestBody payload: ApiOrderPayload
    ): ResponseEntity<ApiOrderPayload> {
        jwt.checkIfAuthorized(payload.hostName)

        // Special handling for Arkivverket is required for the storage systems
        val owner =
            if (payload.hostName == HostName.ASTA) {
                Owner.ARKIVVERKET
            } else {
                Owner.NB
            }

        payload.validate()

        // Return 200 OK and existing order if it exists
        getOrder.getOrder(payload.hostName, payload.hostOrderId)?.let {
            return ResponseEntity
                .status(HttpStatus.OK)
                .body(it.toApiOrderPayload())
        }

        val createdOrder = createOrder.createOrder(payload.toCreateOrderDTO(owner))

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(createdOrder.toApiOrderPayload())
    }

    @Operation(
        summary = "Retrieves order information from Hermes WLS",
        description = """Endpoint for receiving detailed order information from our system, with updated status information.
            Order status is updated based on information provided from the storage systems.
            As such there might be a delay in the status update.
            Some systems don't give any status updates and the order might be stuck in "NOT_STARTED" status until it's manually marked as "COMPLETED".
            The caller must "own" the order, e.g. be the creator of the order."""
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = """Information about the order with given "hostname" and "hostOrderId"""",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiOrderPayload::class)
                )
            ]
        ),
        ApiResponse(
            responseCode = "400",
            description = """Some fields in your request are invalid.
                The error message contains information about the invalid fields.""",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "401",
            description = """Client sending the request is not authorized to request order info, or this order does not belong to them.""",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "403",
            description = """A valid "Authorization" header is missing from the request.""",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "404",
            description = """The order with given "hostname" and "hostOrderId" does not exist in the system.""",
            content = [Content(schema = Schema())]
        )
    )
    @GetMapping("/order/{hostName}/{hostOrderId}")
    suspend fun getOrder(
        @AuthenticationPrincipal jwt: JwtAuthenticationToken,
        @Parameter(
            description = """Name of the host system which made the order.""",
            required = true,
            allowEmptyValue = false,
            example = "AXIELL"
        )
        @PathVariable("hostName") hostName: HostName,
        @Parameter(
            description = """ID of the order which you wish to retrieve.""",
            required = true,
            allowEmptyValue = false,
            example = "mlt-12345-order"
        )
        @PathVariable("hostOrderId") hostOrderId: String
    ): ResponseEntity<ApiOrderPayload> {
        jwt.checkIfAuthorized(hostName)

        val order = getOrder.getOrder(hostName, hostOrderId) ?: return ResponseEntity.notFound().build()

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(order.toApiOrderPayload())
    }

    @Operation(
        summary = "Update an existing order in the WLS",
        description = """Updates the specified order in Hermes WLS and appropriate storage systems.
            The order must have a status of "NOT_STARTED" to be updated.
            The caller must "own" the order, e.g. be the creator of the order."""
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = """The order was updated, and sent to appropriate systems.
                Returns the updated order information for inspection.""",
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
            description = """Client sending the request is not authorized to update orders, or this order does not belong to them.""",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "403",
            description = """A valid "Authorization" header is missing from the request.""",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "409",
            description = """The order is already being processed, and can not be edited at this point.""",
            content = [Content(schema = Schema())]
        )
    )
    @PutMapping("/order")
    suspend fun updateOrder(
        @AuthenticationPrincipal jwt: JwtAuthenticationToken,
        @RequestBody payload: ApiUpdateOrderPayload
    ): ResponseEntity<ApiOrderPayload> {
        jwt.checkIfAuthorized(payload.hostName)
        payload.validate()

        val updatedOrder =
            updateOrder.updateOrder(
                hostName = payload.hostName,
                hostOrderId = payload.hostOrderId,
                itemHostIds = payload.orderLine.map { it.hostId },
                orderType = payload.orderType,
                contactPerson = payload.contactPerson,
                address = payload.address,
                note = payload.note,
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
            description = """Order with given "hostName" and "hostOrderId" was deleted from the system.""",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "401",
            description = """Client sending the request is not authorized to delete orders, or this order does not belong to them."""
        ),
        ApiResponse(
            responseCode = "403",
            description = """A valid "Authorization" header is missing from the request, or the caller is not authorized to delete the order."""
        ),
        ApiResponse(
            responseCode = "404",
            description = """Order with given "hostName" and "hostOrderId" does not exist in the system."""
        )
    )
    @DeleteMapping("/order/{hostName}/{hostOrderId}")
    suspend fun deleteOrder(
        @PathVariable hostName: HostName,
        @PathVariable hostOrderId: String,
        @AuthenticationPrincipal jwt: JwtAuthenticationToken
    ): ResponseEntity<String> {
        jwt.checkIfAuthorized(hostName)

        if (hostOrderId.isBlank()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("The order's hostOrderId is required, and can not be blank")
        }

        try {
            deleteOrder.deleteOrder(hostName, hostOrderId)
        } catch (_: OrderNotFoundException) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok().build()
    }
}
