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
    fun setOrderLines(listOfHostIds: List<String>): Order {
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

    fun setOrderLineStatus(
        hostId: String,
        status: OrderItem.Status
    ): Order {
        if (isOrderClosed()) {
            throw IllegalOrderStateException("Order is already closed with status: $status")
        }

        val updatedOrderLineList =
            orderLine.map {
                if (it.hostId == hostId) {
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

    fun setReceiver(receiver: Receiver): Order {
        return this.copy(receiver = receiver)
    }

    fun setOrderType(orderType: Type): Order {
        return this.copy(orderType = orderType)
    }

    fun setCallbackUrl(callbackUrl: String): Order {
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
     * Throws an exception if the order has been started or finished
     */
    fun throwIfInProgress() {
        if (this.isOrderClosed()) {
            throw IllegalOrderStateException("The order is already completed, and can therefore not be deleted")
        }
        if (this.isOrderProcessingStarted()) {
            throw IllegalOrderStateException("The order can not be deleted as it is already being processed")
        }
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
