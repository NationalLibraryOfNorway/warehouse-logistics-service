package no.nb.mlt.wls.application.kardexapi.kardex

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import no.nb.mlt.wls.domain.ports.inbound.PickOrderItems
import no.nb.mlt.wls.domain.ports.inbound.StockCount
import no.nb.mlt.wls.domain.ports.inbound.UpdateItem
import no.nb.mlt.wls.domain.ports.outbound.DELIMITER
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/hermes/kardex/v1")
@Tag(name = "Kardex", description = "API for receiving material and order updates from Kardex in Hermes WLS")
class KardexController(
    private val updateItem: UpdateItem,
    private val pickOrderItems: PickOrderItems,
    private val stockCount: StockCount
) {
    @PostMapping("material-update")
    suspend fun materialUpdate(
        @RequestBody @Valid payloads: List<KardexMaterialUpdatePayload>
    ): ResponseEntity<Unit> {
        payloads.forEach { material ->
            updateItem.updateItem(material.toUpdateItemPayload())
        }

        return ResponseEntity.ok().build()
    }

    @PostMapping("order-update")
    suspend fun transaction(
        @RequestBody @Valid payloads: List<KardexTransactionPayload>
    ): ResponseEntity<Unit> {
        payloads.forEach { order ->
            val normalizedOrderId = normalizeOrderId(order.hostOrderId)
            updateItem.updateItem(order.toUpdateItemPayload())
            pickOrderItems.pickOrderItems(order.hostName, order.mapToOrderItems(), normalizedOrderId)
        }

        return ResponseEntity.ok().build()
    }

    @PostMapping("stock-sync")
    suspend fun syncMaterialStock(
        @RequestBody @Valid payloads: List<KardexSyncMaterialPayload>
    ): ResponseEntity<Unit> {
        stockCount.countStock(payloads.toStockCountPayload())
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
