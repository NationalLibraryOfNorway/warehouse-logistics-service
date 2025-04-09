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
        logger.info { "CEPA: Processing storage event outbox" }

        val outboxMessages = catalogEventRepository.getUnprocessedSortedByCreatedTime()

        if (outboxMessages.isEmpty()) {
            logger.info { "CEPA: No messages in outbox" }
            return
        }

        logger.info { "CEPA: Processing ${outboxMessages.size} outbox messages" }

        // Possible technical issue here, duplicate IDs, mayhaps should introduce some better handling, if we think this is a possible issue.
        val messageGroups =
            outboxMessages.groupBy {
                when (it) {
                    is ItemEvent -> it.item.hostId
                    is OrderEvent -> it.order.hostOrderId
                }
            }

        logger.info { "CEPA: There are ${messageGroups.keys.size} message groups" }

        messageGroups.forEach {
            handleEventGroup(it)
        }
    }

    private suspend fun handleEventGroup(eventGroup: Map.Entry<String, List<CatalogEvent>>) {
        logger.info { "CEPA: Processing message group with id: ${eventGroup.key}" }

        try {
            eventGroup.value.forEach { handleEvent(it) }
        } catch (e: Exception) {
            logger.error(e) { "CEPA: Error occurred while processing event in message group: ${eventGroup.key}" }
        }
    }

    override suspend fun handleEvent(event: CatalogEvent) {
        logger.info { "CEPA: Processing catalog event: $event" }

        when (event) {
            is ItemEvent -> handleItemUpdate(event)
            is OrderEvent -> handleOrderUpdate(event)
        }

        val processedEvent = catalogEventRepository.markAsProcessed(event)
        logger.info { "CEPA: Marked event as processed: $processedEvent" }
    }

    private suspend fun handleItemUpdate(event: ItemEvent) {
        logger.info { "CEPA: Processing ItemUpdate: $event" }

        inventoryNotifier.itemChanged(event.item, event.eventTimestamp)
    }

    private suspend fun handleOrderUpdate(event: OrderEvent) {
        logger.info { "CEPA: Processing OrderUpdate: $event" }

        inventoryNotifier.orderChanged(event.order, event.eventTimestamp)
    }
}
