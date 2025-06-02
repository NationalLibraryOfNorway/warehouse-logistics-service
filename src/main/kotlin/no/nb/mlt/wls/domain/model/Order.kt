package no.nb.mlt.wls.domain.model

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nb.mlt.wls.domain.NullableNotBlank
import no.nb.mlt.wls.domain.ports.inbound.IllegalOrderStateException
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import java.net.URI

data class Order(
    val hostName: HostName,
    val hostOrderId: String,
    val status: Status,
    val orderLine: List<OrderItem>,
    val orderType: Type,
    val address: Address?,
    val contactPerson: String,
    val contactEmail: String?,
    val note: String?,
    val callbackUrl: String
) {
    // This is only used by order updating
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
        if (isOrderClosed() && status != OrderItem.Status.RETURNED) {
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

    private fun setContactPerson(contactPerson: String): Order = this.copy(contactPerson = contactPerson)

    private fun setOrderType(orderType: Type): Order = this.copy(orderType = orderType)

    private fun setCallbackUrl(callbackUrl: String): Order {
        throwIfInvalidUrl(callbackUrl)

        return this.copy(callbackUrl = callbackUrl)
    }

    private fun updateOrderStatusFromOrderLines(): Order {
        // This might benefit from a small refactor of sorts
        return when {
            orderLine.all(OrderItem::isReturned) -> {
                this.copy(status = Status.RETURNED)
            }

            orderLine.all(OrderItem::isComplete) -> {
                this.copy(status = Status.COMPLETED)
            }

            orderLine.all(OrderItem::isPickedOrFailed) -> {
                this.copy(status = Status.COMPLETED)
            }

            orderLine.all { it.status == OrderItem.Status.NOT_STARTED } -> {
                this.copy(status = Status.NOT_STARTED)
            }

            else -> this.copy(status = Status.IN_PROGRESS)
        }
    }

    private fun isOrderClosed(): Boolean = listOf(Status.COMPLETED, Status.DELETED, Status.RETURNED).contains(status)

    private fun isOrderProcessingStarted(): Boolean = status != Status.NOT_STARTED

    /**
     * Makes a copy of the order with the updated fields
     */
    fun updateOrder(
        itemIds: List<String>,
        callbackUrl: String,
        orderType: Type,
        address: Address?,
        note: String?,
        contactPerson: String
    ): Order {
        throwIfInProgress()

        return this
            .setOrderLines(itemIds)
            .setCallbackUrl(callbackUrl)
            .setOrderType(orderType)
            .setAddress(address)
            .setNote(note)
            .setContactPerson(contactPerson)
    }

    private fun setNote(note: String?): Order = this.copy(note = note)

    private fun setAddress(address: Address?): Order = this.copy(address = address ?: createOrderAddress())

    private fun createOrderAddress(): Address = Address(null, null, null, null, null, null, null)

    /**
     * Delete the order as long as it is possible.
     *
     * This validates if the order can be deleted, and sets the status on it if it can.
     * Deleting it from the storage systems is handled by the outbox processor.
     */
    fun deleteOrder(): Order {
        throwIfInProgress()
        return this.copy(status = Status.DELETED)
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

    fun pickOrder(itemIds: List<String>): Order = this.setOrderLineStatus(itemIds, OrderItem.Status.PICKED)

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
            FAILED,
            RETURNED
        }

        @JsonIgnore
        fun isPickedOrFailed(): Boolean =
            when (this.status) {
                Status.NOT_STARTED -> false
                Status.PICKED -> true
                Status.FAILED -> true
                Status.RETURNED -> false
            }

        @JsonIgnore
        fun isComplete(): Boolean =
            when (this.status) {
                Status.NOT_STARTED -> false
                Status.PICKED -> true
                Status.FAILED -> true
                Status.RETURNED -> true
            }

        @JsonIgnore
        fun isReturned(): Boolean = this.status == Status.RETURNED
    }

    data class Address(
        @field:NullableNotBlank(message = "Invalid address: recipient must not be blank if defined")
        val recipient: String?,
        @field:NullableNotBlank(message = "Invalid address: address line must not be blank if defined")
        val addressLine1: String?,
        @field:NullableNotBlank(message = "Invalid address: address line must not be blank if defined")
        val addressLine2: String?,
        @field:NullableNotBlank(message = "Invalid address: postcode must not be blank if defined")
        val postcode: String?,
        @field:NullableNotBlank(message = "Invalid address: city must not be blank if defined")
        val city: String?,
        @field:NullableNotBlank(message = "Invalid address: region must not be blank if defined")
        val region: String?,
        @field:NullableNotBlank(message = "Invalid address: country must not be blank if defined")
        val country: String?
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
