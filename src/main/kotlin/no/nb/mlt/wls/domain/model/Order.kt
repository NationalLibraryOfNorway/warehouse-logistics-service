package no.nb.mlt.wls.domain.model

import no.nb.mlt.wls.domain.model.Order.OrderItem.Status.FAILED
import no.nb.mlt.wls.domain.model.Order.OrderItem.Status.PICKED
import no.nb.mlt.wls.domain.ports.inbound.IllegalOrderStateException
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import java.net.URI

data class Order(
    val hostName: HostName,
    val hostOrderId: String,
    val status: Status,
    val orderLine: List<OrderItem>,
    val orderType: Type,
    val owner: Owner,
    val receiver: Receiver,
    val callbackUrl: String
) {
    private fun setOrderLines(listOfHostIds: List<String>): Order {
        if (isOrderProcessingStarted()) {
            throw IllegalOrderStateException("Order processing is already started")
        }

        return this.copy(
            orderLine =
                listOfHostIds.map {
                    OrderItem(it, OrderItem.Status.NOT_STARTED)
                }
        )
    }

    private fun setOrderLineStatus(
        hostId: List<String>,
        status: OrderItem.Status
    ): Order {
        if (isOrderClosed()) {
            throw IllegalOrderStateException("Order is already closed with status: $status")
        }

        val updatedOrderLineList =
            orderLine.map {
                if (hostId.contains(it.hostId)) {
                    it.copy(status = status)
                } else {
                    it
                }
            }

        if (updatedOrderLineList == orderLine) {
            throw IllegalOrderStateException("Order line item not found: $hostId")
        }

        return this
            .copy(orderLine = updatedOrderLineList)
            .updateOrderStatusFromOrderLines()
    }

    private fun setReceiver(receiver: Receiver): Order {
        return this.copy(receiver = receiver)
    }

    private fun setOrderType(orderType: Type): Order {
        return this.copy(orderType = orderType)
    }

    private fun setCallbackUrl(callbackUrl: String): Order {
        throwIfInvalidUrl(callbackUrl)

        return this.copy(callbackUrl = callbackUrl)
    }

    private fun updateOrderStatusFromOrderLines(): Order {
        return when {
            orderLine.all(OrderItem::isPickedOrFailed) -> {
                this.copy(status = Status.COMPLETED)
            }

            orderLine.all { it.status == OrderItem.Status.NOT_STARTED } -> {
                this.copy(status = Status.NOT_STARTED)
            }

            else -> this.copy(status = Status.IN_PROGRESS)
        }
    }

    private fun isOrderClosed(): Boolean {
        return listOf(Status.COMPLETED, Status.DELETED).contains(status)
    }

    private fun isOrderProcessingStarted(): Boolean {
        return status != Status.NOT_STARTED
    }

    /**
     * Makes a copy of the order with the updated fields
     */
    fun updateOrder(
        itemIds: List<String>,
        callbackUrl: String,
        orderType: Type,
        receiver: Receiver
    ): Order {
        throwIfInProgress()

        return this.setOrderLines(itemIds)
            .setCallbackUrl(callbackUrl)
            .setOrderType(orderType)
            .setReceiver(receiver)
    }

    /**
     * Delete the order as long as it is possible
     */
    fun deleteOrder() {
        throwIfInProgress()
    }

    /**
     * Throws an exception if the order has been started or finished
     */
    private fun throwIfInProgress() {
        if (this.isOrderClosed()) {
            throw IllegalOrderStateException("The order is already completed, and can therefore not be deleted")
        }
        if (this.isOrderProcessingStarted()) {
            throw IllegalOrderStateException("The order can not be deleted as it is already being processed")
        }
    }

    fun pickOrder(itemIds: List<String>): Order {
        return this.setOrderLineStatus(itemIds, PICKED)
    }

    private fun throwIfInvalidUrl(url: String) {
        runCatching {
            URI(url).toURL().toURI()
        }.exceptionOrNull()?.let {
            throw ValidationException("Invalid URL: $url", it)
        }
    }

    data class OrderItem(
        val hostId: String,
        val status: Status
    ) {
        enum class Status {
            NOT_STARTED,
            PICKED,
            FAILED
        }
    }

    data class Receiver(
        val name: String,
        val address: String?
    )

    enum class Status {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED,
        DELETED,
        RETURNED
    }

    enum class Type {
        LOAN,
        DIGITIZATION
    }
}

fun Order.OrderItem.isPickedOrFailed(): Boolean {
    return this.status == PICKED || this.status == FAILED
}
