package no.nb.mlt.wls.application.kardexapi.kardex

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import no.nb.mlt.wls.domain.ports.inbound.OrderStatusUpdate
import no.nb.mlt.wls.domain.ports.inbound.PickOrderItems
import no.nb.mlt.wls.domain.ports.inbound.SynchronizeItems
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
    private val orderStatusUpdate: OrderStatusUpdate,
    private val synchronizeItems: SynchronizeItems
) {
    @PostMapping("material-update")
    suspend fun materialUpdate(
        @RequestBody @Valid payload: KardexMaterialUpdatePayload
    ): ResponseEntity<Unit> {
        updateItem.updateItem(payload.toUpdateItemPayload())

        return ResponseEntity.ok().build()
    }

    @PostMapping("order-transaction")
    suspend fun transaction(
        @RequestBody @Valid payload: KardexTransactionPayload
    ): ResponseEntity<Unit> {
        val normalizedOrderId = normalizeOrderId(payload.masterOrderId)

        pickOrderItems.pickOrderItems(payload.hostName, payload.mapToOrderItems(), normalizedOrderId)
        return ResponseEntity.ok().build()
    }

    @PostMapping("synchronize")
    suspend fun syncMaterialStock(
        @RequestBody @Valid payload: KardexSyncMaterialPayload
    ): ResponseEntity<Unit> {
        synchronizeItems.synchronizeItems(payload.mapToSyncItems())
        return ResponseEntity.ok().build()
    }

    private fun normalizeOrderId(orderId: String): String {
        val orderIdWithoutPrefix = orderId.substringAfter(DELIMITER, orderId)
        // Could ensure we filtered out a known HostName, but that feels like an overkill since delimiter is kinda unique.
        if (orderIdWithoutPrefix == orderId) {
            logger.warn { "Order ID $orderId doesn't have a prefix, might not be our order, trying regardless" }
        }
        return orderIdWithoutPrefix
    }
}
