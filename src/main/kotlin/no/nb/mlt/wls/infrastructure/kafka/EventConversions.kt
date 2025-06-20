package no.nb.mlt.wls.infrastructure.kafka

import no.nb.mlt.wls.domain.model.events.catalog.ItemEvent
import no.nb.mlt.wls.domain.model.events.catalog.OrderEvent
import no.nb.mlt.wls.domain.model.events.storage.ItemCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderCreated
import no.nb.mlt.wls.domain.model.events.storage.OrderDeleted
import no.nb.mlt.wls.domain.model.statistics.ItemStatisticsEvent
import no.nb.mlt.wls.domain.model.statistics.OrderStatisticsEvent

fun ItemEvent.toStatisticsEvent(): ItemStatisticsEvent =
    ItemStatisticsEvent(
        itemId = item.hostId,
        eventType = "ItemUpdate",
        details =
            mapOf(
                "host" to item.hostName,
                "description" to item.description,
                "category" to item.itemCategory,
                "preferredEnvironment" to item.preferredEnvironment,
                "location" to item.location,
                "quantity" to item.quantity
            )
    )

fun ItemCreated.toStatisticsEvent(): ItemStatisticsEvent =
    ItemStatisticsEvent(
        itemId = createdItem.hostId,
        eventType = "ItemCreate",
        details =
            mapOf(
                "host" to createdItem.hostName,
                "description" to createdItem.description,
                "category" to createdItem.itemCategory,
                "preferredEnvironment" to createdItem.preferredEnvironment,
                "location" to createdItem.location,
                "quantity" to createdItem.quantity
            )
    )

fun OrderEvent.toStatisticsEvent(): OrderStatisticsEvent =
    OrderStatisticsEvent(
        orderId = order.hostOrderId,
        eventType = "OrderUpdate",
        details =
            mapOf(
                "host" to order.hostName,
                "type" to order.orderType,
                "status" to order.status,
                "orderLines" to order.orderLine
            )
    )

fun OrderCreated.toStatisticsEvent(): OrderStatisticsEvent =
    OrderStatisticsEvent(
        orderId = createdOrder.hostOrderId,
        eventType = "OrderCreate",
        details =
            mapOf(
                "host" to createdOrder.hostName,
                "type" to createdOrder.orderType,
                "status" to createdOrder.status,
                "orderLines" to createdOrder.orderLine
            )
    )

fun OrderDeleted.toStatisticsEvent(): OrderStatisticsEvent =
    OrderStatisticsEvent(
        orderId = hostOrderId,
        eventType = "OrderDelete",
        details =
            mapOf(
                "host" to host
            )
    )
