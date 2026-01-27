package no.nb.mlt.wls.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.events.storage.ItemCreated
import no.nb.mlt.wls.domain.model.events.storage.ItemEdited
import no.nb.mlt.wls.domain.model.events.storage.OrderCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderDeleted
import no.nb.mlt.wls.domain.model.events.storage.StorageEvent
import no.nb.mlt.wls.domain.ports.outbound.EventProcessor
import no.nb.mlt.wls.domain.ports.outbound.EventRepository
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.StatisticsService
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import no.nb.mlt.wls.domain.ports.outbound.exceptions.NotSupportedException
import no.nb.mlt.wls.domain.ports.outbound.exceptions.ResourceNotFoundException
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class StorageEventProcessorAdapter(
    private val storageEventRepository: EventRepository<StorageEvent>,
    private val storageSystems: List<StorageSystemFacade>,
    private val itemRepository: ItemRepository,
    private val statisticsService: StatisticsService
) : EventProcessor<StorageEvent> {
    override suspend fun processOutbox() {
        logger.trace { "Processing storage event outbox" }

        val outboxMessages = storageEventRepository.getUnprocessedSortedByCreatedTime()

        if (outboxMessages.isEmpty()) {
            logger.debug { "No messages in outbox" }
            return
        }

        logger.trace { "Processing ${outboxMessages.size} outbox messages" }

        // Possible technical issue here, duplicate IDs, mayhaps should introduce some better handling, if we think this is a possible issue.
        val messageGroups =
            outboxMessages.groupBy {
                when (it) {
                    is ItemCreated -> it.createdItem.hostId
                    is ItemEdited -> it.editInfo.editedItem.hostId
                    is OrderCreated -> it.createdOrder.hostOrderId
                    is OrderDeleted -> it.hostOrderId
                }
            }

        logger.trace { "There are ${messageGroups.keys.size} message groups" }

        messageGroups.forEach {
            handleEventGroup(it)
        }

        logger.trace { "Finished processing storage event outbox" }
    }

    private suspend fun handleEventGroup(eventGroup: Map.Entry<String, List<StorageEvent>>) {
        logger.trace { "Processing message group with id: ${eventGroup.key}" }

        try {
            eventGroup.value.forEach { handleEvent(it) }
        } catch (e: Exception) {
            logger.error(e) { "Error occurred while processing event in message group: ${eventGroup.key}" }
        }
    }

    override suspend fun handleEvent(event: StorageEvent) {
        logger.trace { "Processing storage event: $event" }

        try {
            when (event) {
                is ItemCreated -> handleItemCreated(event)
                is ItemEdited -> handleItemEdited(event)
                is OrderCreated -> handleOrderCreated(event)
                is OrderDeleted -> handleOrderDeleted(event)
            }
        } catch (e: NotSupportedException) {
            logger.warn { "Can not handle event ${event.id}: ${e.message}" }
            logger.warn { "Event was not processed" }
            return
        }

        val processedEvent = storageEventRepository.markAsProcessed(event)
        logger.debug { "Marked event as processed: $processedEvent" }

        statisticsService.recordStatisticsEvent(event)
    }

    private suspend fun handleItemCreated(event: ItemCreated) {
        logger.trace { "Processing ItemCreated: $event" }

        val item = event.createdItem
        val storageCandidates = findValidStorageCandidates(item)

        if (storageCandidates.isEmpty()) {
            logger.warn { "Could not find a storage system to handle item: $item" }
            return
        }

        storageCandidates.forEach {
            it.createItem(item)
            logger.info { "Created item [$item] in storage system: $it" }
        }
    }

    private suspend fun handleItemEdited(event: ItemEdited) {
        logger.trace { "Processing ItemEdited: $event" }

        val item = event.editInfo.editedItem
        val oldItem = event.editInfo.oldItem
        val newStorageCandidates = findValidStorageCandidates(item)
        val oldStorageCandidates = findValidStorageCandidates(oldItem)

        if (newStorageCandidates.isEmpty()) {
            logger.warn { "Updated item [$item] does not have a storage system that can handle it" }
            return
        }

        if (item.quantity == 0) {
            newStorageCandidates.forEach { storageCandidate ->
                logger.info {
                    "Updating ${item.hostId} for ${item.hostName} in $storageCandidate"
                }
                try {
                    storageCandidate.editItem(item)
                } catch (e: ResourceNotFoundException) {
                    logger.warn {
                        "Could not find item, trying to create it instead"
                    }
                    storageCandidate.createItem(item)
                    logger.info { "Created item ${item.hostId} for ${item.hostName} in storage system $storageCandidate" }
                } catch (e: Exception) {
                    logger.error(e) { "Error occurred while editing item $item" }
                }
            }
            logger.info {
                """
                Item ${item.hostId} metadata changed from this:
                    [$oldItem]
                to this:
                    [$item]
                old storage candidates:
                    $oldStorageCandidates
                new storage candidates:
                    $newStorageCandidates
                """.trimIndent()
            }
        } else if (item.quantity == 1) {
            logger.warn {
                """
                Item ${item.hostId} metadata changed from this:
                    [$oldItem]
                to this:
                    [$item]
                old storage candidates:
                    $oldStorageCandidates
                new storage candidates:
                    $newStorageCandidates
                No changes were made to storages as the items need to be ordered out from the system
                """.trimIndent()
            }
        }
    }

    private suspend fun handleOrderCreated(event: OrderCreated) {
        logger.trace { "Processing OrderCreated: $event" }

        val createdOrder = event.createdOrder

        val items =
            itemRepository.getItemsByIds(
                createdOrder.hostName,
                createdOrder.orderLine.map { it.hostId }
            )

        mapItemsOnAssociatedStorage(items).forEach { (storageSystemFacade, itemList) ->
            if (storageSystemFacade == null) {
                logger.warn { "Could not find a storage system to handle items: $itemList" }
            }

            val orderCopy =
                createdOrder.copy(
                    orderLine =
                        itemList.map {
                            Order.OrderItem(it.hostId, Order.OrderItem.Status.NOT_STARTED)
                        }
                )

            storageSystemFacade?.createOrder(orderCopy)
            logger.info { "Created order [$orderCopy] in storage system: ${storageSystemFacade ?: "none"}" }
        }
    }

    private suspend fun handleOrderDeleted(event: OrderDeleted) {
        logger.trace { "Processing OrderDeleted: $event" }

        storageSystems.forEach {
            it.deleteOrder(event.hostOrderId, event.host)
        }
    }

    private suspend fun mapItemsOnAssociatedStorage(items: List<Item>): Map<StorageSystemFacade?, List<Item>> =
        items.groupBy { item ->
            storageSystems.firstOrNull { it.isInStorage(item.associatedStorage) }
        }

    private suspend fun findValidStorageCandidates(item: Item): List<StorageSystemFacade> = storageSystems.filter { it.canHandleItem(item) }
}
