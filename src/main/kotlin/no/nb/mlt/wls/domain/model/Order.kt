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
    val productLine: List<OrderItem>,
    val orderType: Type,
    val owner: Owner?,
    val receiver: Receiver,
    val callbackUrl: String
) {
    fun setProductLines(listOfHostIds: List<String>): Order {
        if (isOrderProcessingStarted()) {
            throw IllegalOrderStateException("Order processing is already started")
        }

        return this.copy(
            productLine =
                listOfHostIds.map {
                    OrderItem(it, OrderItem.Status.NOT_STARTED)
                }
        )
    }

    fun setProductLineStatus(
        hostId: String,
        status: OrderItem.Status
    ): Order {
        if (isOrderClosed()) {
            throw IllegalOrderStateException("Order is already closed with status: $status")
        }

        val updatedProductLineList =
            productLine.map {
                if (it.hostId == hostId) {
                    it.copy(status = status)
                } else {
                    it
                }
            }

        if (updatedProductLineList == productLine) {
            throw IllegalOrderStateException("Product line item not found: $hostId")
        }

        return this
            .copy(productLine = updatedProductLineList)
            .updateOrderStatusFromProductLines()
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

    private fun updateOrderStatusFromProductLines(): Order {
        return when {
            productLine.all(OrderItem::isPickedOrFailed) -> {
                this.copy(status = Status.COMPLETED)
            }

            productLine.all { it.status == Order.OrderItem.Status.NOT_STARTED } -> {
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
        val address: String
    )

    enum class Status {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED,
        DELETED
    }

    enum class Type {
        LOAN,
        DIGITIZATION
    }
}

fun Order.OrderItem.isPickedOrFailed(): Boolean {
    return this.status == PICKED || this.status == FAILED
}
