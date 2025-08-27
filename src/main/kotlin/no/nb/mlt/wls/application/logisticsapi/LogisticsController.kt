package no.nb.mlt.wls.application.logisticsapi

import io.swagger.v3.oas.annotations.tags.Tag
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.inbound.GetItems
import no.nb.mlt.wls.domain.ports.inbound.GetOrders
import no.nb.mlt.wls.domain.ports.outbound.ReportItemAsMissing
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/logistics/v1")
@Tag(name = "Logistics endpoints", description = """API for managing logistics within Hermes WLS""")
class LogisticsController(
    private val getItems: GetItems,
    private val getOrders: GetOrders,
    private val reportItemMissing: ReportItemAsMissing
) {
    @GetMapping("/order")
    suspend fun getOrderWithDetails(
        @RequestParam hostNames: List<HostName>?,
        @RequestParam hostId: String
    ): ResponseEntity<Any> {
        val hostNames = hostNames ?: HostName.entries.toList()
        val orders = getOrders.getOrdersById(hostNames, hostId)
        val detailedOrders =
            orders.map { order ->
                val itemIds = order.orderLine.map { it.hostId }
                val items = getItems.getItemsByIds(order.hostName, itemIds)
                order.toDetailedOrder(items)
            }
        return ResponseEntity.ok(detailedOrders)
    }

    @PutMapping("/item/{hostName}/{hostId}/report-missing")
    suspend fun reportItemMissing(
        @PathVariable hostName: HostName,
        @PathVariable hostId: String
    ): ResponseEntity<ApiItemPayload> {
        val item = reportItemMissing.reportItemMissing(hostName, hostId)
        return ResponseEntity.ok(item.toApiPayload())
    }
}
