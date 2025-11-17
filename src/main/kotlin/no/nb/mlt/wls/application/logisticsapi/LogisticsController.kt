package no.nb.mlt.wls.application.logisticsapi

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.nb.mlt.wls.application.hostapi.item.ApiItemPayload
import no.nb.mlt.wls.application.hostapi.item.toApiPayload
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.ports.inbound.GetItems
import no.nb.mlt.wls.domain.ports.inbound.GetOrders
import no.nb.mlt.wls.domain.ports.inbound.ReportItemAsMissing
import no.nb.mlt.wls.domain.ports.outbound.DELIMITER
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@ConditionalOnProperty(value = ["logistics.enabled"], havingValue = "true")
@RestController
@RequestMapping("/hermes/logistics/v1")
@Tag(name = "Logistics endpoints", description = """API for managing logistics within Hermes WLS""")
class LogisticsController(
    private val getItems: GetItems,
    private val getOrders: GetOrders,
    private val reportItemMissing: ReportItemAsMissing
) {
    private val logger = KotlinLogging.logger {}

    @Operation(
        summary = "Retrieves detailed order from the storage system",
        description = """Endpoint for receiving detailed order and item information from our system.
            The full item data is included in the order lines, including their relevant order status.
            Order status is updated based on information provided from the storage systems.
            As such there might be a delay in the status update.
            Some systems don't give any status updates and the order might be stuck in "NOT_STARTED" status until it's manually marked as "COMPLETED".
        """
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = """Information about orders with the given "hostName"s and "hostOrderId"s with their associated item details.""",
            content = [
                Content(
                    schema =
                        Schema(
                            implementation = ApiDetailedOrder::class
                        )
                )
            ]
        ),
        ApiResponse(
            responseCode = "401",
            description = """Client sending the request is not authorized to get detailed orders.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Unauthorized")
                )
            ]
        ),
        ApiResponse(
            responseCode = "403",
            description = """A valid "Authorization" header is missing from the request, or the caller is not authorized to get the order.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Forbidden")
                )
            ]
        )
    )
    @GetMapping("/order")
    suspend fun getOrderWithDetails(
        @RequestParam hostNames: List<HostName>?,
        @RequestParam hostId: String
    ): ResponseEntity<List<ApiDetailedOrder>> {
        val hostNames = hostNames ?: HostName.entries.toList()
        val normalizedOrderKey = normalizeOrderId(hostId)

        val orders =
            normalizedOrderKey?.let {
                getOrders.getOrdersById(listOf(it.first), it.second)
            } ?: getOrders.getOrdersById(hostNames, hostId)

        if (orders.isEmpty()) {
            return ResponseEntity.notFound().build()
        }
        val detailedOrders =
            orders.map { order ->
                val itemIds = order.orderLine.map { it.hostId }
                val items = getItems.getItemsByIds(order.hostName, itemIds)
                order.toDetailedOrder(items)
            }
        return ResponseEntity.ok(detailedOrders)
    }

    @Operation(
        summary = "Report and mark an item as missing in Hermes WLS",
        description = """Endpoint for reporting missing items.
            This is intended to be used by storage handlers to let the system know that an item could not be found.
            Usually this happens after an extensive search.
            When this happens the item is updated, and an event is sent to the host that the item is missing.
            This endpoint will also update any related orders that contain the missing items, which also sends updates to the host.
        """
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = """Item was successfully marked as missing, and relevant orders were updated.""",
            content = [
                Content(
                    schema =
                        Schema(
                            implementation = ApiItemPayload::class
                        )
                )
            ]
        ),
        ApiResponse(
            responseCode = "401",
            description = """Client sending the request is not authorized to report items as missing.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Unauthorized")
                )
            ]
        ),
        ApiResponse(
            responseCode = "403",
            description = """A valid "Authorization" header is missing from the request, or the caller is not authorized to update the item.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Forbidden")
                )
            ]
        ),
        ApiResponse(
            responseCode = "404",
            description = """The item with given "hostname" and "hostId" does not exist in the system.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Not Found")
                )
            ]
        )
    )
    @PutMapping("/item/{hostName}/{hostId}/report-missing")
    suspend fun reportItemMissing(
        @PathVariable hostName: HostName,
        @PathVariable hostId: String
    ): ResponseEntity<ApiItemPayload> {
        val item = reportItemMissing.reportItemMissing(hostName, hostId)
        return ResponseEntity.ok(item.toApiPayload())
    }

    private fun normalizeOrderId(orderId: String): Pair<HostName, String>? {
        // i.e. split AXIELL-SD---mlt-123 into [AXIELL-SD, mlt-123]
        val fragments = orderId.split(DELIMITER)
        if (fragments.size != 2) {
            return null
        }
        // remove storage indicator from hostName, E.G. AXIELL-SD into AXIELL
        val potentialHost = fragments.first().substringBefore("-")
        val hostName = HostName.fromString(potentialHost)
        val orderId = fragments[1]
        return hostName to orderId
    }

    @Operation(
        summary = "Retrieves items by their host ID",
        description = """Endpoint for retrieving items based on their host ID.
            This is useful for fetching item details when only the host ID is known.
            If multiple items share the same host ID, all matching items will be returned.
        """
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = """A list of items matching the provided host ID.""",
            content = [
                Content(
                    schema =
                        Schema(
                            implementation = ApiItem::class
                        )
                )
            ]
        ),
        ApiResponse(
            responseCode = "401",
            description = """Client sending the request is not authorized to get items.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Unauthorized")
                )
            ]
        ),
        ApiResponse(
            responseCode = "403",
            description = """A valid "Authorization" header is missing from the request, or the caller is not authorized to update the item.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Forbidden")
                )
            ]
        ),
        ApiResponse(
            responseCode = "404",
            description = """No items with given "hostId" found.""",
            content = [
                Content(
                    mediaType = "string",
                    schema = Schema(implementation = String::class, example = "Not Found")
                )
            ]
        )
    )
    @GetMapping("/item")
    suspend fun getItems(
        @Parameter(
            description = """The item ID from the host system.""",
            example = "mlt-12345",
            required = true,
            allowEmptyValue = false
        )
        @RequestParam hostId: String
    ): ResponseEntity<List<ApiItem>> {
        logger.debug { "Getting items for $hostId" }
        val items = getItems.getItemsById(hostId).map(Item::toApiItem)

        if (items.isEmpty()) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok(items)
    }
}
