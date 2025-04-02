package no.nb.mlt.wls.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.events.catalog.CatalogEvent
import no.nb.mlt.wls.domain.model.events.catalog.ItemEvent
import no.nb.mlt.wls.domain.model.events.catalog.OrderEvent
import no.nb.mlt.wls.domain.ports.outbound.EventProcessor
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.InventoryNotifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@Service
class CatalogEventProcessorAdapter(
    private val catalogEventRepository: EventRepository<CatalogEvent>,
    private val inventoryNotifier: InventoryNotifier
) : EventProcessor<CatalogEvent> {
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    suspend fun processOutbox() {
        val outboxMessages =
            catalogEventRepository
                .getUnprocessedSortedByCreatedTime()

        if (outboxMessages.isNotEmpty()) {
            logger.info { "Processing ${outboxMessages.size} outbox messages" }
            outboxMessages.forEach { handleEvent(it) }
        }
    }

    override suspend fun handleEvent(event: CatalogEvent) {
        when (event) {
            is ItemEvent -> handleItemUpdate(event)
            is OrderEvent -> handleOrderUpdate(event)
        }

        val processedEvent = catalogEventRepository.markAsProcessed(event)
        logger.info { "Marked event as processed: $processedEvent" }
    }

    private suspend fun handleItemUpdate(event: ItemEvent) {
        logger.info { "Processing ItemUpdate: $event" }

        inventoryNotifier.itemChanged(event.item, event.eventTimestamp)
    }

    private suspend fun handleOrderUpdate(event: OrderEvent) {
        logger.info { "Processing OrderUpdate: $event" }

        inventoryNotifier.orderChanged(event.order, event.eventTimestamp)
    }
}
