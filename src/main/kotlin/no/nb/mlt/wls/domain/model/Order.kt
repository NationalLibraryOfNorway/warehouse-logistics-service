package no.nb.mlt.wls.domain.model

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nb.mlt.wls.domain.ports.inbound.exceptions.IllegalOrderStateException
import no.nb.mlt.wls.domain.ports.inbound.exceptions.ValidationException
import kotlin.collections.all
import kotlin.collections.map

/**
 * Represents an order containing a set of order lines, along with associated metadata such as status, type, and contact details.
 * This class supports managing the state and data of an order, including updating its status
 * and handling order line items for operations like picking or returning items.
 *
 * @property hostName The host associated with the order, indicating where the order originates.
 * @property hostOrderId The unique identifier for the order provided by the host system.
 * @property status The current status of the order, represented by the Status enum.
 * @property orderLine The list of order items associated with this order.
 * @property orderType The type of the order, represented by the Type enum.
 * @property address The recipient's address for the order. Can be null if no address is provided.
 * @property contactPerson The name of the contact person associated with the order.
 * @property contactEmail The email address of the contact person. Can be null if not provided.
 * @property note An optional note or comment about the order.
 * @property callbackUrl The URL that can be used as a callback endpoint for order-related operations.
 */
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
    /**
     * Updates the status of the specified order items to `PICKED`.
     *
     * @param itemIds The list of unique identifiers for the items to be marked as `PICKED`.
     * @return The updated instance of the order with the affected item's statuses set to `PICKED`.
     * @throws IllegalOrderStateException If the order is already closed or completed, prohibiting status changes.
     * @throws ValidationException If any of the specified item IDs do not exist in the order.
     */
    fun pick(itemIds: List<String>): Order = this.setOrderLineStatus(itemIds, OrderItem.Status.PICKED)

    /**
     * Updates the status of the specified order items to `RETURNED`.
     *
     * @param itemIds The list of unique identifiers for the items to be marked as `RETURNED`.
     * @return The updated instance of the order with the affected item's statuses set to `RETURNED`.
     * @throws IllegalOrderStateException If the order is already closed or completed, prohibiting status changes.
     * @throws ValidationException If any of the specified item IDs do not exist in the order.
     */
    fun returnItems(itemIds: List<String>): Order = this.setOrderLineStatus(itemIds, OrderItem.Status.RETURNED)

    /**
     * Updates the status of the specified order items to `MISSING`.
     *
     * @param itemIds The list of unique identifiers for the items to be marked as `MISSING`.
     * @return The updated instance of the order with the affected item's statuses set to `MISSING`.
     * @throws IllegalOrderStateException If the order is already closed or completed, prohibiting status changes.
     * @throws ValidationException If any of the specified item IDs do not exist in the order.
     */
    fun markMissing(itemIds: List<String>): Order {
        val validLines =
            this.orderLine
                .filter { itemIds.contains(it.hostId) }
                .filter { it.status == OrderItem.Status.NOT_STARTED }
                .map { it.hostId }

        return this.setOrderLineStatus(validLines, OrderItem.Status.MISSING)
    }

    /**
     * Marks the order as deleted by updating its status to `DELETED`.
     *
     * @return The updated instance of the order with its status set to `DELETED`.
     * @throws IllegalOrderStateException if the current status prohibits the deletion of the order.
     */
    @Throws(IllegalOrderStateException::class)
    fun delete(): Order = updateStatus(Status.DELETED)

    /**
     * Updates the status of the order to the specified new status.
     * To change the order status, it cannot be considered closed (all items are returned, or it was deleted).
     * Additionally, the status progression must make sense, e.g. we cannot go from COMPLETED to NOT_STARTED.
     *
     * @param newStatus The new status to set for the order.
     * @return The updated instance of the order with the new status.
     * @throws IllegalOrderStateException if the order is already closed or if the status progression is invalid.
     */
    @Throws(IllegalOrderStateException::class)
    fun updateStatus(newStatus: Status): Order {
        if (isClosed()) {
            throw IllegalOrderStateException("The order is already closed, and can therefore not be changed")
        }

        // In progress orders cannot be changed to "not started"
        // Picked orders cannot be set to in progress
        val isInvalidProgression =
            (newStatus == Status.NOT_STARTED) ||
                (newStatus == Status.IN_PROGRESS && isPicked()) ||
                (newStatus == Status.DELETED && status != Status.NOT_STARTED)

        if (isInvalidProgression) {
            throw IllegalOrderStateException(
                "The order status can not be updated in an invalid direction. Tried updating from ${this.status} to $newStatus"
            )
        }

        return this.copy(status = newStatus)
    }

    /**
     * Checks whether the order is considered closed.
     *
     * An order is classified as closed if its status is either `DELETED` or `RETURNED`.
     *
     * @return true if the order status is `DELETED` or `RETURNED`, false otherwise.
     */
    fun isClosed(): Boolean = status in listOf(Status.DELETED, Status.RETURNED)

    private fun setOrderLineStatus(
        itemIds: List<String>,
        status: OrderItem.Status
    ): Order {
        if (itemIds.isEmpty()) return this

        if (isClosed()) {
            throw IllegalOrderStateException("Order is already closed with status: ${this.status}")
        }

        if (isPicked() && status != OrderItem.Status.RETURNED) {
            throw IllegalOrderStateException("Order is already complete with status: ${this.status}")
        }

        if (hasUnknownItems(itemIds.toSet())) {
            throw ValidationException("Can't update order line items that do not exist in the order: $itemIds")
        }

        val updatedOrderLineList =
            orderLine.map { orderItem ->
                if (orderItem.hostId in itemIds) orderItem.copy(status = status) else orderItem
            }

        return this
            .copy(orderLine = updatedOrderLineList)
            .updateStatusFromOrderLines()
    }

    /**
     * Determines if the given set of item IDs contains any unknown items,
     * i.e., items that do not correspond to the existing host IDs in the order lines.
     *
     * @param itemIds The set of unique identifiers for the items to be checked.
     * @return `true` if there are unknown items in the provided set, `false` otherwise.
     */
    private fun hasUnknownItems(itemIds: Set<String>): Boolean {
        val existingHostIds = orderLine.map { it.hostId }.toSet()
        val unknownItemIds = itemIds - existingHostIds

        return unknownItemIds.isNotEmpty()
    }

    /**
     * Updates the status of the order based on the statuses of all its order lines.
     *
     * The method evaluates the status of every [OrderItem] in the `orderLine` collection and updates the overall
     * order status accordingly:
     * - If all [OrderItem] are marked as `RETURNED`, the order status is updated to `RETURNED`.
     * - If all [OrderItem] are marked as `COMPLETE`, the order status is updated to `COMPLETED`.
     * - If all [OrderItem] are either `PICKED` or `FAILED`, the order status is also updated to `COMPLETED`.
     * - If all [OrderItem] have a status of `NOT_STARTED`, the order status is updated to `NOT_STARTED`.
     * - For all other combinations of [OrderItem] statuses, the order status is updated to `IN_PROGRESS`.
     *
     * @return The updated [Order] instance with its new status.
     */
    private fun updateStatusFromOrderLines(): Order {
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

            orderLine.all(OrderItem::isNotStarted) -> {
                updateStatus(Status.NOT_STARTED)
            }

            else -> updateStatus(Status.IN_PROGRESS)
        }
    }

    /**
     * Check whether the order is completed by having all its items picked.
     */
    fun isPicked(): Boolean = status == Status.COMPLETED

    /**
     * Represents an ordered item with its ID and status.
     *
     * @property hostId The unique identifier for the item in the host system.
     * @property status The status of the item, represented by the Status enum.
     */
    data class OrderItem(
        val hostId: String,
        val status: Status
    ) {
        /**
         * Represents the possible statuses for an order item.
         *
         * The Status enum defines the various stages or states an order item can be in during its lifecycle.
         *
         * NOT_STARTED: Indicates that the order process has not begun for the item.
         * PICKED: Indicates that the item has been picked and is ready for further processing or delivery.
         * MISSING: Indicates that the item could not be picked due to it being missing.
         * FAILED: Indicates that an item could not be picked due to an error in the system, being damaged or cancelled.
         * RETURNED: Indicates that the item has been returned to its original location or a different one.
         */
        enum class Status {
            NOT_STARTED,
            PICKED,
            FAILED,
            MISSING,
            RETURNED
        }

        /**
         * Determines whether the order item was PICKED or FAILED.
         *
         * @return `true` if the status is PICKED or FAILED, `false` otherwise.
         */
        @JsonIgnore
        fun isPickedOrFailed(): Boolean = this.status in listOf(Status.PICKED, Status.FAILED)

        /**
         * Determines whether the order item's status is considered complete.
         * A status is considered complete if it is PICKED, FAILED, or RETURNED.
         *
         * @return `true` if the status is PICKED, FAILED, or RETURNED, `false` if the status is NOT_STARTED.
         */
        @JsonIgnore
        fun isComplete(): Boolean = this.status != Status.NOT_STARTED

        /**
         * Determines whether the order item's status is RETURNED.
         *
         * @return true if the status is RETURNED, false otherwise.
         */
        @JsonIgnore
        fun isReturned(): Boolean = this.status == Status.RETURNED

        /**
         * Determines whether the order item's status is NOT_STARTED.
         *
         * @return true if the status is NOT_STARTED, false otherwise.
         */
        @JsonIgnore
        fun isNotStarted(): Boolean = this.status == Status.NOT_STARTED
    }

    /**
     * Represents an address associated with an order.
     *
     * This class stores detailed address information including recipient name,
     * address lines, postal code, city, region, and country.
     *
     * @property recipient The name of the person or entity receiving at the address.
     * @property addressLine1 The first line of the address, typically including street name and number.
     * @property addressLine2 The second line of the address, often used for supplemental information like apartment or suite details.
     * @property postcode The postal code associated with the address.
     * @property city The city where the address is located.
     * @property region The region or state of the address.
     * @property country The country where the address is located.
     */
    data class Address(
        val recipient: String?,
        val addressLine1: String?,
        val addressLine2: String?,
        val postcode: String?,
        val city: String?,
        val region: String?,
        val country: String?
    )

    /**
     * Represents the current state of an order.
     *
     * Used to track the lifecycle of an order from its initiation to its resolution or closure.
     * The states include:
     *
     * - NOT_STARTED: The order has been created but is yet to be processed.
     * - IN_PROGRESS: The order is currently being processed.
     * - COMPLETED: The order has been successfully processed and finalized.
     * - DELETED: The order has been removed or marked for deletion.
     * - RETURNED: The order or its items have been returned to the storage.
     */
    enum class Status {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED,
        DELETED,
        RETURNED
    }

    /**
     * Represents the type of the order.
     * It specifies whether the order is related to borrowing items (LOAN)
     * or is associated with digitization requests (DIGITIZATION).
     */
    enum class Type {
        LOAN,
        DIGITIZATION
    }
}
