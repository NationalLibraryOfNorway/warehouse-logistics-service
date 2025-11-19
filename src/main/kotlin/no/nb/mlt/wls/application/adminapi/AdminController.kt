package no.nb.mlt.wls.application.adminapi

import io.swagger.v3.oas.annotations.tags.Tag
import no.nb.mlt.wls.domain.model.events.catalog.CatalogEvent
import no.nb.mlt.wls.domain.model.events.email.EmailEvent
import no.nb.mlt.wls.domain.model.events.storage.StorageEvent
import no.nb.mlt.wls.domain.ports.outbound.EventProcessor
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/hermes-admin/v1")
@Tag(name = "Administration endpoints", description = """API for administrative tasks""")
class AdminController(
    private val catalogEventProcessor: EventProcessor<CatalogEvent>,
    private val storageEventProcessor: EventProcessor<StorageEvent>,
    private val emailEventProcessor: EventProcessor<EmailEvent>,
) {
    @PostMapping("/process-all-outboxes")
    suspend fun processOutboxEvents() {
        listOf(catalogEventProcessor, storageEventProcessor, emailEventProcessor)
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

    @PostMapping("/process-email-outbox")
    suspend fun processEmailEvents() {
        emailEventProcessor.processOutbox()
    }
}
