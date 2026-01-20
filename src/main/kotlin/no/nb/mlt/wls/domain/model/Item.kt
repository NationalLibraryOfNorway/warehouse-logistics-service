package no.nb.mlt.wls.domain.model

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.ports.inbound.exceptions.ValidationException

private val logger = KotlinLogging.logger {}

/**
 * Marks the location of an item that is with a lender.
 * Ensures we use the same value across the application to avoid confusion.
 */
const val WITH_LENDER_LOCATION = "WITH_LENDER"

/**
 * Marks the location of an item when we don't know where it is.
 * Used when the item is just created and we don't have a location for it yet.
 * Ensures we use the same value across the application to avoid confusion.
 */
const val UNKNOWN_LOCATION = "UNKNOWN"

/**
 * Marks the location of an item as missing.
 * Used by the storage handlers to indicate that an item previously had a known location, but is missing.
 */
const val MISSING = "MISSING"

/**
 * Represents an item in the storage system.
 * It contains information about the item that is relevant for the storage operations, it should not contain any catalog-specific information.
 *
 * @property hostId The unique identifier for the item in the host system.
 * @property hostName The name of the host system which owns the item.
 * @property description A brief description of the item.
 * @property itemCategory The category of the item.
 * @property preferredEnvironment The preferred storage environment for the item.
 * @property packaging The packaging type of the item.
 * @property callbackUrl An optional URL for callbacks related to the item.
 * @property location The current location of the item.
 * @property quantity The quantity of the item available in stock.
 * @property associatedStorage The storage system the item was last seen in, which determines where orders are sent.
 */
data class Item(
    val hostId: String,
    val hostName: HostName,
    val description: String,
    val itemCategory: ItemCategory,
    val preferredEnvironment: Environment,
    val packaging: Packaging,
    val callbackUrl: String?,
    val location: String = UNKNOWN_LOCATION,
    val quantity: Int = 0,
    val associatedStorage: AssociatedStorage,
    val confidential: Boolean = false
) {
    init {
        if (location.isBlank()) {
            throw ValidationException("Location can not be blank")
        }
        if (location == UNKNOWN_LOCATION && quantity != 0) {
            throw ValidationException("Location must be set when quantity is not zero")
        }
    }

    /**
     * Picks a specified amount of items from the stock.
     * If the amount picked exceeds the available quantity, it logs an error and sets the quantity
     * to zero, indicating that the item is no longer available.
     * If after picking the quantity becomes zero, it sets the location to [WITH_LENDER_LOCATION].
     *
     * @param amountPicked The number of items to pick from the stock.
     * @return The updated Item instance with the new quantity.
     */
    fun pick(amountPicked: Int): Item {
        val itemsInStockQuantity = quantity
        var location = this.location

        // In the case of over-picking, log it and set the quantity to zero.
        // This is done in the hope that on return the database recovers
        if (amountPicked > itemsInStockQuantity) {
            logger.error {
                "Tried to pick too many items for item with id '$hostId'. " +
                    "WLS DB has '$itemsInStockQuantity' stocked, and storage system tried to pick '$amountPicked'"
            }
        }

        val quantity = Math.clamp(itemsInStockQuantity.minus(amountPicked).toLong(), 0, Int.MAX_VALUE)
        if (quantity == 0) {
            location = WITH_LENDER_LOCATION
        }

        return this.copy(
            quantity = quantity,
            location = location
        )
    }

    /**
     * Synchronizes the quantity and location of the item.
     *
     * This function should be used during stock sync updates from storage systems.
     * Unlike the [move] function, which should be used when an item is physically moved within the system.
     *
     * Constraints:
     * - If [quantity] is not zero, then the [location] can not be null.
     * - Updates from other storage systems are ignored if the quantity is zero.
     * - Updates where [location] is null and current location is [WITH_LENDER_LOCATION] will be ignored.
     *
     * @param quantity The new quantity to set for the item.
     * @param location The new location to set for the item.
     * @param associatedStorage The storage system sending this stock update.
     * @throws ValidationException if the constraints are violated.
     * @return The updated Item instance.
     */
    @Throws(ValidationException::class)
    fun synchronizeItem(
        quantity: Int,
        location: String?,
        associatedStorage: AssociatedStorage
    ): Item {
        if (this.associatedStorage != associatedStorage && quantity == 0) {
            // Ignore updates from other systems if the quantity is 0
            return this
        }
        if (location == null && quantity != 0) {
            throw ValidationException("Location must be set when quantity is not zero")
        }

        // do not override with lender location
        if (location == null && this.location == WITH_LENDER_LOCATION) {
            return this
        }

        return this.copy(
            quantity = quantity,
            location = location ?: UNKNOWN_LOCATION,
            associatedStorage = associatedStorage
        )
    }

    /**
     * Moves an item within the logistics system.
     *
     * This function should be used to update the location, quantity, and associated storage of an item
     * when the item is physically moved within the system.
     * Unlike [synchronizeItem] which should be used when syncing stock with given storage system.
     *
     * Constraints:
     * - [location] must not be blank.
     * - If [quantity] is not zero, [location] must not be [UNKNOWN_LOCATION].
     *
     * @param location The new location of the item. Must not be blank.
     * @param quantity The new quantity of the item.
     * @param associatedStorage The storage system sending this item move update.
     * @throws ValidationException if the constraints are violated.
     * @return The updated Item instance.
     */
    @Throws(ValidationException::class)
    fun move(
        location: String,
        quantity: Int,
        associatedStorage: AssociatedStorage
    ): Item {
        if (location.isBlank()) {
            throw ValidationException("Location can not be blank")
        }

        if (location == UNKNOWN_LOCATION && quantity != 0) {
            throw ValidationException("Location must be set when quantity is not zero")
        }

        return this.copy(location = location, quantity = quantity, associatedStorage = associatedStorage)
    }

    fun edit(
        description: String,
        itemCategory: ItemCategory,
        preferredEnvironment: Environment,
        packaging: Packaging,
        callbackUrl: String?
    ): Item =
        Item(
            hostId = this.hostId,
            hostName = this.hostName,
            description = description,
            itemCategory = itemCategory,
            preferredEnvironment = preferredEnvironment,
            packaging = packaging,
            callbackUrl = if (callbackUrl.isNullOrBlank()) this.callbackUrl else callbackUrl,
            location = this.location,
            quantity = this.quantity,
            associatedStorage = this.associatedStorage,
            confidential = this.confidential
        )

    /**
     * Marks the location as missing.
     * Use it to indicate that we do not know where the current whereabouts of the item is.
     * Sets quantity to zero and location to [MISSING]
     */
    fun reportMissing(): Item = this.copy(quantity = 0, location = MISSING, associatedStorage = AssociatedStorage.UNKNOWN)

    fun isSameItem(other: Item): Boolean = other.hostId == hostId && other.hostName == hostName
}
