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
    override suspend fun processOutbox() {
        logger.trace { "Processing catalog event outbox" }

        val outboxMessages = catalogEventRepository.getUnprocessedSortedByCreatedTime()

        if (outboxMessages.isEmpty()) {
            logger.debug { "No messages in outbox" }
            return
        }

        logger.trace { "Processing ${outboxMessages.size} outbox messages" }

        // Possible technical issue here, duplicate IDs, mayhaps should introduce some better handling, if we think this is a possible issue.
        val messageGroups =
            outboxMessages.groupBy {
                when (it) {
                    is ItemEvent -> it.item.hostId
                    is OrderEvent -> it.order.hostOrderId
                }
            }

        logger.debug { "There are ${messageGroups.keys.size} message groups" }

        messageGroups.forEach {
            handleEventGroup(it)
        }

        logger.trace { "Finished processing catalog event outbox" }
    }

    private suspend fun handleEventGroup(eventGroup: Map.Entry<String, List<CatalogEvent>>) {
        logger.trace { "Processing message group with id: ${eventGroup.key}" }

        try {
            eventGroup.value.forEach { handleEvent(it) }
        } catch (e: Exception) {
            logger.error(e) { "Error occurred while processing event in message group: ${eventGroup.key}" }
        }
    }

    override suspend fun handleEvent(event: CatalogEvent) {
        logger.trace { "Processing catalog event: $event" }

        when (event) {
            is ItemEvent -> handleItemUpdate(event)
            is OrderEvent -> handleOrderUpdate(event)
        }

        val processedEvent = catalogEventRepository.markAsProcessed(event)
        logger.info { "Marked event as processed: $processedEvent" }
    }

    private suspend fun handleItemUpdate(event: ItemEvent) {
        logger.trace { "Processing ItemUpdate: $event" }

        inventoryNotifier.itemChanged(event.item, event.eventTimestamp, event.id)
    }

    private suspend fun handleOrderUpdate(event: OrderEvent) {
        logger.trace { "Processing OrderUpdate: $event" }

        inventoryNotifier.orderChanged(event.order, event.eventTimestamp, event.id)
    }
}
