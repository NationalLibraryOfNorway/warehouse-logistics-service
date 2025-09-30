package no.nb.mlt.wls.application.kardexapi.kardex

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.inbound.GetItems
import no.nb.mlt.wls.domain.ports.inbound.PickOrderItems
import no.nb.mlt.wls.domain.ports.inbound.StockCount
import no.nb.mlt.wls.domain.ports.inbound.UpdateItem
import no.nb.mlt.wls.domain.ports.inbound.exceptions.DuplicateItemException
import no.nb.mlt.wls.domain.ports.inbound.exceptions.ItemNotFoundException
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
    private val stockCount: StockCount,
    private val getItems: GetItems
) {
    @PostMapping("material-update")
    suspend fun materialUpdate(
        @RequestBody @Valid payloads: List<KardexMaterialPayload>
    ): ResponseEntity<Unit> {
        payloads.forEach { material ->
            material.validate()
            val resolvedHostName = getHostNameForItem(material.hostName, material.hostId)
            val validMaterial = material.copy(hostName = resolvedHostName.toString())

            updateItem.updateItem(validMaterial.toUpdateItemPayload())
        }

        return ResponseEntity.ok().build()
    }

    @PostMapping("order-update")
    suspend fun transactionUpdate(
        @RequestBody @Valid payloads: List<KardexTransactionPayload>
    ): ResponseEntity<Unit> {
        payloads.forEach { order ->
            val normalizedOrderId = normalizeOrderId(order.hostOrderId)
            val resolvedHostName = getHostNameForItem(order.hostName, order.hostId)
            val validOrder = order.copy(hostName = resolvedHostName.toString())

            updateItem.updateItem(validOrder.toUpdateItemPayload())
            pickOrderItems.pickOrderItems(resolvedHostName, validOrder.mapToOrderItems(), normalizedOrderId)
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

    private suspend fun getHostNameForItem(
        hostName: String,
        hostId: String
    ): HostName {
        val parsedHostName =
            try {
                HostName.fromString(hostName)
            } catch (e: IllegalArgumentException) {
                logger.warn { "Invalid hostname '$hostName' provided, defaulting to UNKNOWN. Error: ${e.message}" }
                HostName.UNKNOWN
            }

        if (parsedHostName != HostName.UNKNOWN) {
            return parsedHostName
        }

        val itemsById = getItems.getItemsById(hostId)

        if (itemsById.size > 1) {
            logger.error { "Found multiple items with same Host ID: $hostId" }
            logger.error { "Items: ${itemsById.joinToString { i -> "${i.hostName} ${i.hostId} ${i.description}"}}" }
            throw DuplicateItemException("Found multiple items with same Host ID: $hostId")
        }

        if (itemsById.isEmpty()) {
            logger.error { "Item with id '$hostId' does not exist" }
            throw ItemNotFoundException("Item with id '$hostId' does not exist")
        }

        return itemsById.first().hostName
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
