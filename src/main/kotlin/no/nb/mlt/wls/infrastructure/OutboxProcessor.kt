package no.nb.mlt.wls.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.model.outboxMessages.ItemCreated
import no.nb.mlt.wls.domain.model.outboxMessages.OrderCreated
import no.nb.mlt.wls.domain.model.outboxMessages.OrderDeleted
import no.nb.mlt.wls.domain.model.outboxMessages.OrderUpdated
import no.nb.mlt.wls.domain.model.outboxMessages.OutboxMessage
import no.nb.mlt.wls.domain.ports.outbound.EmailNotifier
import no.nb.mlt.wls.domain.ports.outbound.ItemRepository
import no.nb.mlt.wls.domain.ports.outbound.OutboxMessageProcessor
import no.nb.mlt.wls.domain.ports.outbound.OutboxRepository
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@Service
class OutboxProcessor(
    private val outboxRepository: OutboxRepository,
    private val storageSystems: List<StorageSystemFacade>,
    private val itemRepository: ItemRepository,
    private val emailNotifier: EmailNotifier
) : OutboxMessageProcessor {
    // TODO: Should be configurable number of seconds
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    suspend fun processOutbox() {
        val outboxMessages =
            outboxRepository
                .getUnprocessedSortedByCreatedTime()

        if (outboxMessages.isNotEmpty()) {
            logger.info { "Processing ${outboxMessages.size} outbox messages" }
            outboxMessages.forEach { handleEvent(it) }
        }
    }

    override suspend fun handleEvent(event: OutboxMessage) {
        when (event) {
            is ItemCreated -> handleItemCreated(event)
            is OrderCreated -> handleOrderCreated(event)
            is OrderDeleted -> handleOrderDeleted(event)
            is OrderUpdated -> handleOrderUpdated(event)
        }

        val processedEvent = outboxRepository.markAsProcessed(event)
        logger.info { "Marked event as processed: $processedEvent" }
    }

    private fun handleOrderDeleted(event: OrderDeleted) {
        TODO("Not yet implemented")
    }

    private suspend fun handleOrderUpdated(event: OrderUpdated) {
        logger.info { "Processing OrderUpdated: $event" }
        val updatedOrder = event.updatedOrder
        val items =
            itemRepository.getItems(
                updatedOrder.orderLine.map { it.hostId },
                updatedOrder.hostName
            )

        mapItemsOnLocation(items).forEach { (storageSystemFacade, itemList) ->
            if (storageSystemFacade == null) {
                logger.info { "Could not find a storage system to handle items: $itemList" }
            }
            storageSystemFacade?.updateOrder(updatedOrder)
        }
    }

    private suspend fun handleItemCreated(event: ItemCreated) {
        logger.info { "Processing ItemCreated: $event" }
        val item = event.createdItem
        val storageCandidates = findValidStorages(item)
        if (storageCandidates.isEmpty()) {
            logger.info { "Could not find a storage system to handle item: $item" }
            return
        }
        storageCandidates.forEach {
            it.createItem(item)
        }
    }

    private suspend fun handleOrderCreated(event: OrderCreated) {
        logger.info { "Processing OrderCreated: $event" }
        val createdOrder = event.createdOrder

        val items =
            itemRepository.getItems(
                createdOrder.orderLine.map { it.hostId },
                createdOrder.hostName
            )

        mapItemsOnLocation(items).forEach { (storageSystemFacade, itemList) ->
            if (storageSystemFacade == null) {
                logger.info { "Could not find a storage system to handle items: $itemList" }
            }

            val orderCopy =
                createdOrder.copy(
                    orderLine =
                        itemList.map {
                            Order.OrderItem(it.hostId, Order.OrderItem.Status.NOT_STARTED)
                        }
                )
            storageSystemFacade?.createOrder(orderCopy)
            createAndSendEmails(orderCopy)
        }
    }

    private suspend fun mapItemsOnLocation(items: List<Item>): Map<StorageSystemFacade?, List<Item>> {
        return items.groupBy { item ->
            storageSystems.firstOrNull { it.canHandleLocation(item.location) }
        }
    }

    private suspend fun findValidStorages(item: Item): List<StorageSystemFacade> {
        return storageSystems.filter { it.canHandleItem(item) }
    }

    private suspend fun createAndSendEmails(order: Order) {
        val items = order.orderLine.map { it.hostId }
        val orderItems = itemRepository.getItems(items, order.hostName)
        if (orderItems.isNotEmpty()) {
            emailNotifier.orderCreated(order, orderItems)
        }
    }
}
