package no.nb.mlt.wls.application.adminapi

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.events.catalog.CatalogEvent
import no.nb.mlt.wls.domain.model.events.storage.StorageEvent
import no.nb.mlt.wls.domain.ports.inbound.GetItems
import no.nb.mlt.wls.domain.ports.inbound.GetOrder
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.domain.ports.outbound.EventProcessor
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/hermes-admin/v1")
@Tag(name = "Administration endpoints", description = """API for administrative tasks""")
class AdminController(
    private val catalogEventProcessor: EventProcessor<CatalogEvent>,
    private val storageEventProcessor: EventProcessor<StorageEvent>,
    private val getOrder: GetOrder,
    private val getItems: GetItems,
    private val emailNotifier: EmailNotifier
) {
    @PostMapping("/process-all-outboxes")
    suspend fun processOutboxEvents() {
        listOf(catalogEventProcessor, storageEventProcessor)
            .forEach { it.processOutbox() }
    }

    @PostMapping("/process-catalog-outbox")
    suspend fun processCatalogEvents() {
        catalogEventProcessor.processOutbox()
    }

    @PostMapping("/process-storage-outbox")
    suspend fun processStorageEvents() {
        storageEventProcessor.processOutbox()
    }

    @PostMapping("/resend-storage-email/{hostName}/{hostOrderId}")
    suspend fun resendStorageHandlerEmail(
        @Parameter(
            description = """Name of the host system which made the order.""",
            required = true,
            allowEmptyValue = false,
            example = "AXIELL"
        )
        @PathVariable("hostName")
        hostName: HostName,
        @Parameter(
            description = """ID of the order which you wish to resend.""",
            required = true,
            allowEmptyValue = false,
            example = "mlt-12345-order"
        )
        @PathVariable("hostOrderId")
        hostOrderId: String
    ): ResponseEntity<Unit> {
        // TODO - Put in WLSService, or put somewhere nicer?
        val order = getOrder.getOrder(hostName, hostOrderId) ?: return ResponseEntity.notFound().build()
        val items =
            getItems.getItemsByIds(
                hostName,
                order.orderLine.map { it.hostId }
            )
        emailNotifier.sendOrderHandlerMessage(order, items)
        return ResponseEntity.ok().build()
    }
}
