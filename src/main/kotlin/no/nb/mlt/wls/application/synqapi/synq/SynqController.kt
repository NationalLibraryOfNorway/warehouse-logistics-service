package no.nb.mlt.wls.application.synqapi.synq

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.callbacks.Callback
import io.swagger.v3.oas.annotations.callbacks.Callbacks
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.ports.inbound.MoveItem
import no.nb.mlt.wls.domain.ports.inbound.OrderStatusUpdate
import no.nb.mlt.wls.domain.ports.inbound.PickItems
import no.nb.mlt.wls.domain.ports.inbound.PickOrderItems
import no.nb.mlt.wls.infrastructure.callbacks.NotificationItemPayload
import no.nb.mlt.wls.infrastructure.callbacks.NotificationOrderPayload
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping(path = ["/synq/v1"])
@Tag(name = "SynQ Controller", description = "API for receiving product and order updates from SynQ in Hermes WLS")
class SynqController(
    private val moveItem: MoveItem,
    private val pickItems: PickItems,
    private val pickOrderItems: PickOrderItems,
    private val orderStatusUpdate: OrderStatusUpdate
) {
    @Operation(
        summary = "Updates the status and location for items",
        description = "Parses all the items from the SynQ load unit, and updates both status & location for them."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Item with given 'hostName' and 'hostId' was found and updated.",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "400",
            description = "The payload for moving items was invalid and nothing got updated.",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "403",
            description = "A valid 'Authorization' header is missing from the request.",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "404",
            description = """An item for a specific 'hostName' and 'hostId' was not found.
                Error message contains information about the missing item.""",
            content = [Content(schema = Schema())]
        )
    )
    @Callbacks(
        Callback(
            name = "Item Callback",
            callbackUrlExpression = "\$request.body#/callbackUrl",
            operation =
                arrayOf(
                    Operation(
                        summary = "Notification of updated item",
                        description = """This callback triggers when an item is updated inside the storage systems.
                        It contains the full data of the item, including the current quantity and location of it.
                    """,
                        method = "post",
                        requestBody =
                            SwaggerRequestBody(
                                content =
                                    arrayOf(
                                        Content(schema = Schema(implementation = NotificationItemPayload::class))
                                    ),
                                required = true
                            )
                    )
                )
        )
    )
    @PutMapping("/move-item")
    suspend fun moveItem(
        @RequestBody synqBatchMoveItemPayload: SynqBatchMoveItemPayload
    ): ResponseEntity<Void> {
        synqBatchMoveItemPayload.mapToItemPayloads().map { moveItem.moveItem(it) }
        return ResponseEntity.ok().build()
    }

    @Operation(
        summary = "Confirms the picking of items from a specific SynQ order",
        description = """Updates the items from a specific order when they are picked from a SynQ warehouse.
            This does not update the order status, as SynQ sends an update to the order-update endpoint later.
        """
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "The items were picked successfully.",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Order update payload was invalid and nothing got updated.",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "401",
            description = "Client sending the request is not authorized to update orders.",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "403",
            description = "A valid 'Authorization' header is missing from the request.",
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
                        It contains the data of the complete and updated order.
                    """,
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
    @PutMapping("/pick-update/{owner}/{orderId}")
    suspend fun pickOrder(
        @Parameter(description = "Owner of the order items")
        @PathVariable owner: Owner,
        @Parameter(description = "Order ID in the storage system")
        @PathVariable orderId: String,
        @RequestBody payload: SynqOrderPickingConfirmationPayload
    ) {
        payload.validate()
        val hostIds: MutableMap<String, Double> = mutableMapOf()
        val hostName = HostName.valueOf(payload.orderLine.first().hostName.uppercase())
        payload.orderLine.map { orderLine ->
            hostIds.put(
                orderLine.productId,
                orderLine.quantity
            )
        }

        pickItems.pickItems(hostName, hostIds)
        pickOrderItems.pickOrderItems(hostName, hostIds.keys.toList(), orderId)
    }

    @Operation(
        summary = "Updates order status based on SynQ order status update",
        description = """Finds a specified order and updates its status given the message we receive from SynQ.
            SynQ only sends us the update for the whole order, not for individual products.
            They are updated in the pick-update endpoint.
        """
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = """Order with given 'hostName' and 'orderId' was found and updated.
                The response body contains the updated order.""",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Order update payload was invalid and nothing got updated.",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "401",
            description = "Client sending the request is not authorized to update orders.",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "403",
            description = "A valid 'Authorization' header is missing from the request.",
            content = [Content(schema = Schema())]
        ),
        ApiResponse(
            responseCode = "404",
            description = """Order with given 'hostName' and 'orderId' was not found.
                Error message contains information about the missing order.""",
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
                        It contains the data of the complete and updated order.
                    """,
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
    @PutMapping("/order-update/{owner}/{orderId}")
    suspend fun updateOrder(
        @RequestBody orderUpdatePayload: SynqOrderStatusUpdatePayload,
        @Parameter(description = "Owner of the order items")
        @PathVariable owner: Owner,
        @Parameter(description = "Order ID in the storage system")
        @PathVariable orderId: String
    ): ResponseEntity<String> {
        if (orderId.isBlank()) {
            return ResponseEntity.badRequest().body("Order ID cannot be blank")
        }

        if (orderUpdatePayload.warehouse.isBlank()) {
            return ResponseEntity.badRequest().body("Warehouse cannot be blank")
        }

        orderStatusUpdate.updateOrderStatus(orderUpdatePayload.hostName, orderId, orderUpdatePayload.getConvertedStatus())

        return ResponseEntity.ok().build()
    }
}
