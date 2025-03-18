package no.nb.mlt.wls.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.catalogMessages.CatalogMessage
import no.nb.mlt.wls.domain.model.catalogMessages.ItemUpdate
import no.nb.mlt.wls.domain.model.catalogMessages.OrderUpdate
import no.nb.mlt.wls.domain.ports.outbound.CatalogMessageProcessor
import no.nb.mlt.wls.domain.ports.outbound.CatalogMessageRepository
import no.nb.mlt.wls.domain.ports.outbound.InventoryNotifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@Service
class CatalogMessageProcessorAdapter(
    private val catalogMessageRepository: CatalogMessageRepository,
    private val inventoryNotifier: InventoryNotifier
) : CatalogMessageProcessor {
    // TODO: Should be configurable number of seconds
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    suspend fun processOutbox() {
        val outboxMessages =
            catalogMessageRepository
                .getUnprocessedSortedByCreatedTime()

        if (outboxMessages.isNotEmpty()) {
            logger.info { "Processing ${outboxMessages.size} outbox messages" }
            outboxMessages.forEach { handleEvent(it) }
        }
    }

    override suspend fun handleEvent(event: CatalogMessage) {
        when (event) {
            is ItemUpdate -> handleItemUpdate(event)
            is OrderUpdate -> handleOrderUpdate(event)
        }

        val processedEvent = catalogMessageRepository.markAsProcessed(event)
        logger.info { "Marked event as processed: $processedEvent" }
    }

    private suspend fun handleItemUpdate(event: ItemUpdate) {
        logger.info { "Processing ItemUpdate: $event" }

        inventoryNotifier.itemChanged(event.item)
    }

    private suspend fun handleOrderUpdate(event: OrderUpdate) {
        logger.info { "Processing OrderUpdate: $event" }

        inventoryNotifier.orderChanged(event.order)
    }
}
