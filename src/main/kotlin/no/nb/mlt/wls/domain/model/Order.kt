package no.nb.mlt.wls.domain.model

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nb.mlt.wls.domain.NullableNotBlank
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
    private fun setOrderLineStatus(
        hostIds: List<String>,
        status: OrderItem.Status
    ): Order {
        if (isClosed()) {
            throw ValidationException("Order is already closed with status: $status")
        }

        if (isPicked() && status != OrderItem.Status.RETURNED) {
            throw ValidationException("Order is already complete with status: $status")
        }

        val hasUnknownItems =
            hostIds.any { hostIdToUpdate ->
                orderLine.none { it.hostId == hostIdToUpdate }
            }

        if (hasUnknownItems) {
            throw ValidationException("Can't update order line items that do not exist in the order: $hostIds")
        }

        val updatedOrderLineList =
            hostIds.map { hostIdToUpdate ->
                orderLine.first { it.hostId == hostIdToUpdate }.copy(status = status)
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
                updateStatus(Status.RETURNED)
            }

            orderLine.all(OrderItem::isComplete) -> {
                updateStatus(Status.COMPLETED)
            }

            orderLine.all(OrderItem::isPickedOrFailed) -> {
                updateStatus(Status.COMPLETED)
            }

            orderLine.all { it.status == OrderItem.Status.NOT_STARTED } -> {
                updateStatus(Status.NOT_STARTED)
            }

            else -> updateStatus(Status.IN_PROGRESS)
        }
    }

    /**
     * Order cannot receive any further updates
     */
    fun isClosed(): Boolean = listOf(Status.DELETED, Status.RETURNED).contains(status)

    /**
     * Order is picked and finished, but is not returned yet
     */
    private fun isPicked(): Boolean = listOf(Status.COMPLETED).contains(status)

    private fun isInProgress(): Boolean = status != Status.NOT_STARTED

    private fun setNote(note: String?): Order = this.copy(note = note)

    private fun setAddress(address: Address?): Order = this.copy(address = address ?: createOrderAddress())

    private fun createOrderAddress(): Address = Address(null, null, null, null, null, null, null)

    /**
     * Delete the order as long as it is possible.
     *
     * This validates if the order can be deleted, and sets the status on it if it can.
     * Deleting it from the storage systems is handled by the outbox processor.
     */
    fun deleteOrder(): Order = updateStatus(Status.DELETED)

    fun updateStatus(newStatus: Status): Order {
        if (isClosed()) {
            throw ValidationException("The order is already closed, and can therefore not be changed")
        }

        // In progress orders cannot be changed to not started
        // Picked orders cannot be set to in progress
        val isInvalidTransition =
            (newStatus == Status.NOT_STARTED && isInProgress()) ||
                (newStatus == Status.IN_PROGRESS && isPicked())

        if (isInvalidTransition) {
            throw ValidationException("The order status can not be updated in an invalid direction. Tried updating from ${this.status} to $newStatus")
        }

        return this.copy(status = newStatus)
    }

    fun pickItems(itemIds: List<String>): Order = this.setOrderLineStatus(itemIds, OrderItem.Status.PICKED)

    fun returnItems(itemIds: List<String>): Order = this.setOrderLineStatus(itemIds, OrderItem.Status.RETURNED)

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
