package no.nb.mlt.wls.application.synqapi.synq

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.ports.inbound.MoveItem
import no.nb.mlt.wls.domain.ports.inbound.OrderStatusUpdate
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = [ "/synq/v1"])
@Tag(name = "SynQ Controller", description = "API for receiving product and order updates from SynQ in Hermes WLS")
class SynqController(
    private val moveItem: MoveItem,
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
    @PutMapping("/item-update")
    suspend fun updateItem(
        @AuthenticationPrincipal jwt: JwtAuthenticationToken,
        @RequestBody synqBatchMoveItemPayload: SynqBatchMoveItemPayload
    ): ResponseEntity<Void> {
        for (payload in synqBatchMoveItemPayload.mapToItemPayloads()) {
            moveItem.moveItem(payload)
        }
        return ResponseEntity.ok().build()
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
    @PutMapping("/order-update/{owner}/{orderId}")
    suspend fun updateOrder(
        @AuthenticationPrincipal jwt: JwtAuthenticationToken,
        @RequestBody orderUpdatePayload: SynqOrderStatusUpdatePayload,
        @Parameter(description = "Owner of the order items")
        @PathVariable owner: Owner,
        @Parameter(description = "Order ID in the storage system")
        @PathVariable orderId: String
    ): ResponseEntity<Void> {
        orderStatusUpdate.updateOrderStatus(orderUpdatePayload.hostName, orderId, orderUpdatePayload.getConvertedStatus())

        return ResponseEntity.ok().build()
    }
}
