package no.nb.mlt.wls.application.synqapi.synq

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.inbound.MoveItem
import no.nb.mlt.wls.domain.ports.inbound.PickItems
import no.nb.mlt.wls.domain.ports.inbound.PickOrderItems
import no.nb.mlt.wls.domain.ports.inbound.SynchronizeItems
import no.nb.mlt.wls.domain.ports.inbound.UpdateItem
import no.nb.mlt.wls.domain.ports.inbound.UpdateOrderStatus
import no.nb.mlt.wls.domain.ports.outbound.DELIMITER
import no.nb.mlt.wls.infrastructure.synq.SynqOwner
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.InvalidParameterException

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping(path = ["/hermes/synq/v1"])
@Tag(name = "SynQ Controller", description = """API for receiving product and order updates from SynQ in Hermes WLS""")
class SynqController(
    private val moveItem: MoveItem,
    private val updateItem: UpdateItem,
    private val pickItems: PickItems,
    private val pickOrderItems: PickOrderItems,
    private val updateOrderStatus: UpdateOrderStatus,
    private val synchronizeItems: SynchronizeItems
) {
    @Operation(
        summary = "Updates item's status and location",
        description = """Extracts information about every item, updates their location and quantity,
            and sends an update to the host systems."""
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = """Item with given "hostName" and "hostId" was found and updated.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "OK")
                )
            ]
        ),
        ApiResponse(
            responseCode = "400",
            description = """Moved item payload was invalid and nothing got updated.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Error message")
                )
            ]
        ),
        ApiResponse(
            responseCode = "401",
            description = """Client sending the request is not authorized to update items.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Unauthorized")
                )
            ]
        ),
        ApiResponse(
            responseCode = "403",
            description = """A valid "Authorization" header is missing from the request.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Forbidden")
                )
            ]
        ),
        ApiResponse(
            responseCode = "404",
            description = """An item for a specific "hostName" and "hostId" was not found.
                Error message contains information about the missing item.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Not Found")
                )
            ]
        )
    )
    @PutMapping("/move-item")
    suspend fun moveItem(
        @RequestBody @Valid synqBatchMoveItemPayload: SynqBatchMoveItemPayload
    ): ResponseEntity<Unit> {
        synqBatchMoveItemPayload.loadUnit.forEach { product ->
            if (product.quantityMove != null) {
                moveItem.moveItem(
                    product.toMoveItemPayload(
                        synqBatchMoveItemPayload.prevLocation,
                        synqBatchMoveItemPayload.location
                    )
                )
            } else {
                updateItem.updateItem(
                    product.toUpdateItemPayload(
                        synqBatchMoveItemPayload.location
                    )
                )
            }
        }
        return ResponseEntity.ok().build()
    }

    @Operation(
        summary = "Confirms the picking of items from a specific SynQ order",
        description = """Updates status of items in a specific order when they are picked from a SynQ warehouse.
            This does not update the order status, as SynQ sends an update to the order-update endpoint later."""
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = """The items were picked successfully.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "OK")
                )
            ]
        ),
        ApiResponse(
            responseCode = "400",
            description = """Picking confirmation payload was invalid and nothing got updated.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Error message")
                )
            ]
        ),
        ApiResponse(
            responseCode = "401",
            description = """Client sending the request is not authorized to update orders.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Unauthorized")
                )
            ]
        ),
        ApiResponse(
            responseCode = "403",
            description = """A valid "Authorization" header is missing from the request.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Forbidden")
                )
            ]
        )
    )
    @PutMapping("/pick-update/{owner}/{orderId}")
    suspend fun pickOrder(
        @Parameter(description = "Owner of the order items")
        @PathVariable owner: SynqOwner,
        @Parameter(description = "Order ID in the storage system")
        @PathVariable orderId: String,
        @RequestBody @Valid payload: SynqOrderPickingConfirmationPayload
    ): ResponseEntity<String> {
        if (orderId.isBlank()) {
            return ResponseEntity.badRequest().body("Order ID cannot be blank")
        }

        val orderIdWithoutPrefix = normalizeOrderId(orderId)
        val hostName = payload.getValidHostName()
        val hostIds = payload.mapProductsToQuantity()

        pickItems.pickItems(hostName, hostIds)
        pickOrderItems.pickOrderItems(hostName, hostIds.keys.toList(), orderIdWithoutPrefix)

        return ResponseEntity.ok().build()
    }

    @Operation(
        summary = "Updates order status based on SynQ order status update",
        description = """Finds a specified order and updates its status.
            SynQ only sends the order-update for the whole order, not for individual products in the order.
            They are updated in the pick-update endpoint."""
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = """Order with given "hostName" and "orderId" was found and updated.
                The response body contains the updated order.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "OK")
                )
            ]
        ),
        ApiResponse(
            responseCode = "400",
            description = """Order update payload was invalid and nothing got updated.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Error message")
                )
            ]
        ),
        ApiResponse(
            responseCode = "401",
            description = """Client sending the request is not authorized to update orders.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Unauthorized")
                )
            ]
        ),
        ApiResponse(
            responseCode = "403",
            description = """A valid "Authorization" header is missing from the request.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Forbidden")
                )
            ]
        ),
        ApiResponse(
            responseCode = "404",
            description = """Order with given "hostName" and "orderId" was not found.
                Error message contains information about the missing order.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Not Found")
                )
            ]
        )
    )
    @PutMapping("/order-update/{owner}/{orderId}")
    suspend fun updateOrder(
        @RequestBody @Valid orderUpdatePayload: SynqOrderStatusUpdatePayload,
        @Parameter(description = "Owner of the order items")
        @PathVariable owner: SynqOwner,
        @Parameter(description = "Order ID in the storage system")
        @PathVariable orderId: String
    ): ResponseEntity<String> {
        if (orderId.isBlank()) {
            return ResponseEntity.badRequest().body("Order ID cannot be blank")
        }

        val orderIdWithoutPrefix = normalizeOrderId(orderId)

        if (orderUpdatePayload.warehouse.isBlank()) {
            return ResponseEntity.badRequest().body("Warehouse cannot be blank")
        }

        if (orderUpdatePayload.status == SynqOrderStatus.COMPLETED) {
            // no-op
            return ResponseEntity.ok().build()
        }

        val hostName = HostName.fromString(orderUpdatePayload.sanitizedHostName) // Must be changed when we fix SynQ to use Axiel, not Mavis
        updateOrderStatus.updateOrderStatus(hostName, orderIdWithoutPrefix, orderUpdatePayload.getConvertedStatus())

        return ResponseEntity.ok().build()
    }

    @Operation(
        summary = "Inventory reconciliation",
        description = """Reconciles the inventory in SynQ with the inventory in Hermes WLS."""
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = """Inventory was reconciled successfully.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "OK")
                )
            ]
        ),
        ApiResponse(
            responseCode = "400",
            description = """Inventory reconciliation payload was invalid.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Error message")
                )
            ]
        ),
        ApiResponse(
            responseCode = "401",
            description = """Client sending the request is not authorized.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Unauthorized")
                )
            ]
        ),
        ApiResponse(
            responseCode = "403",
            description = """A valid "Authorization" header is missing from the request.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Forbidden")
                )
            ]
        )
    )
    @PutMapping("/inventory-reconciliation")
    suspend fun inventoryReconciliation(
        @RequestBody payload: SynqInventoryReconciliationPayload
    ): ResponseEntity<Unit> {
        logger.debug { "Reconciliation. loadUnits=${payload.loadUnit.size}" }

        val units =
            payload.loadUnit
                .map {
                    val item =
                        try {
                            val mappedHostName = it.getMappedHostName()

                            if (mappedHostName == null) {
                                logger.warn { "unmapped hostName: ${it.hostName}, skipping: $it." }
                                return@map null
                            }

                            SynchronizeItems.ItemToSynchronize(
                                hostId = it.productId,
                                hostName = mappedHostName,
                                location = it.location,
                                quantity = it.quantityOnHand.toInt(),
                                itemCategory = it.getMappedCategory(),
                                packaging = it.getMappedUOM(),
                                currentPreferredEnvironment = it.mapCurrentPreferredEnvironment(),
                                description = if (it.description.isNullOrBlank()) "-" else it.description
                            )
                        } catch (e: InvalidParameterException) {
                            logger.error {
                                "Error while synchronizing item (hostId: ${it.productId}, hostName: ${it.hostName}). Message: ${e.message}"
                            }
                            null
                        }

                    if (item != null) {
                        logger.debug { "Synchronizing item: $item" }
                    }

                    item
                }.filterNotNull()

        logger.debug { "Synchronizing ${units.size} of ${payload.loadUnit.size} items in message. Skipping ${payload.loadUnit.size - units.size}" }

        try {
            synchronizeItems.synchronizeItems(units)
        } catch (e: Exception) {
            logger.error(e) { "Error while synchronizing items" }
        }

        return ResponseEntity.ok().build()
    }
}

private fun normalizeOrderId(orderId: String): String {
    val orderIdWithoutPrefix = orderId.substringAfter(DELIMITER, orderId)
    // Could ensure we filtered out a known HostName, but that feels like an overkill since delimiter is kinda unique.
    if (orderIdWithoutPrefix == orderId) {
        logger.warn { "Order ID $orderId doesn't have a prefix, might not be our order, trying regardless" }
    }
    return orderIdWithoutPrefix
}
