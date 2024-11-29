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
    val address: Address?,
    val contactPerson: String,
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
        hostIds: List<String>,
        status: OrderItem.Status
    ): Order {
        if (isOrderClosed()) {
            throw IllegalOrderStateException("Order is already closed with status: $status")
        }

        val updatedOrderLineList =
            orderLine.map {
                if (hostIds.contains(it.hostId)) {
                    it.copy(status = status)
                } else {
                    it
                }
            }

        if (updatedOrderLineList == orderLine) {
            throw IllegalOrderStateException("Order line item not found: $hostIds")
        }

        return this
            .copy(orderLine = updatedOrderLineList)
            .updateOrderStatusFromOrderLines()
    }

    private fun setContactPerson(contactPerson: String): Order {
        return this.copy(contactPerson = contactPerson)
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
        contactPerson: String
    ): Order {
        throwIfInProgress()

        return this.setOrderLines(itemIds)
            .setCallbackUrl(callbackUrl)
            .setOrderType(orderType)
            .setContactPerson(contactPerson)
    }

    /**
     * Delete the order as long as it is possible.
     *
     * At the moment this function only does validation, as deleting it from the system is
     * handled by the service and infrastructure
     */
    fun deleteOrder() {
        throwIfInProgress()
    }

    /**
     * Throws an exception if the order has been started or finished
     */
    private fun throwIfInProgress() {
        if (this.isOrderClosed()) {
            throw IllegalOrderStateException("The order is already completed, and can therefore not be changed")
        }
        if (this.isOrderProcessingStarted()) {
            throw IllegalOrderStateException("The order is currently being processed, and can therefore not be changed")
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

    // TODO - Add country field
    data class Address(
        val name: String?,
        val addressLine1: String?,
        val addressLine2: String?,
        val zipcode: String?,
        val city: String?,
        val state: String?
    ) {
        fun validate() {
            if (name?.isBlank() == true) {
                throw ValidationException("Invalid address: name must not be blank")
            }
            if (addressLine1?.isBlank() == true) {
                throw ValidationException("Invalid address: address line must not be blank")
            }
            if (addressLine2?.isBlank() == true) {
                throw ValidationException("Invalid address: address line must not be blank")
            }
            if (zipcode?.isBlank() == true) {
                throw ValidationException("Invalid address: zipcode must not be blank")
            }
            if (city?.isBlank() == true) {
                throw ValidationException("Invalid address: city must not be blank")
            }
            // TODO - Validate State/Region/County?
        }
    }

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
