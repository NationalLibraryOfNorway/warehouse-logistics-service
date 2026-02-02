package no.nb.mlt.wls.application.kardexapi.kardex

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.inbound.GetItems
import no.nb.mlt.wls.domain.ports.inbound.PickOrderItems
import no.nb.mlt.wls.domain.ports.inbound.SynchronizeItems
import no.nb.mlt.wls.domain.ports.inbound.UpdateItem
import no.nb.mlt.wls.domain.ports.inbound.exceptions.DuplicateItemException
import no.nb.mlt.wls.domain.ports.inbound.exceptions.ItemNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.exceptions.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.inbound.exceptions.ValidationException
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
    private val synchronizeItems: SynchronizeItems,
    private val pickOrderItems: PickOrderItems,
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

            try {
                updateItem.updateItem(validMaterial.toUpdateItemPayload())
            } catch (e: ValidationException) {
                logger.warn { "Unable to validate Kardex material update payload. Error: ${e.localizedMessage}" }
                logger.debug { "Payload info: $validMaterial" }
            }
        }

        return ResponseEntity.ok().build()
    }

    @PostMapping("order-update")
    suspend fun transactionUpdate(
        @RequestBody @Valid transactionPayloads: List<KardexTransactionPayload>
    ): ResponseEntity<Unit> {
        transactionPayloads.forEach { payload ->
            val normalizedOrderId = normalizeOrderId(payload.hostOrderId)
            val resolvedHostName = getHostNameForItem(payload.hostName, payload.hostId)
            val validPayload = payload.copy(hostName = resolvedHostName.toString())
            try {
                updateItem.updateItem(validPayload.toUpdateItemPayload())
                pickOrderItems.pickOrderItems(resolvedHostName, validPayload.mapToOrderItems(), normalizedOrderId)
            } catch (e: ValidationException) {
                logger.warn { "Unable to validate Kardex transaction payload. Error: ${e.localizedMessage}" }
                logger.debug { "Payload info: $validPayload" }
            }
            catch (_: OrderNotFoundException) {
                // Since Kardex sends us "order" updates for its pick history, any manual picks will
                // not have an order in Hermes, and will always fail.
                logger.warn {
                    "Order with ID $normalizedOrderId was not found, but item ${validPayload.hostId} was updated."
                }
            }
        }

        return ResponseEntity.ok().build()
    }

    @PostMapping("stock-sync")
    suspend fun syncMaterialStock(
        @RequestBody @Valid payloads: List<KardexSyncMaterialPayload>
    ): ResponseEntity<Unit> {
        synchronizeItems.synchronizeItems(payloads.toSyncPayloads())
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
