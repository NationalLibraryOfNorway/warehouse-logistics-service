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
 */
class Item(
    val hostId: String,
    val hostName: HostName,
    val description: String,
    val itemCategory: ItemCategory,
    val preferredEnvironment: Environment,
    val packaging: Packaging,
    val callbackUrl: String?,
    location: String?,
    quantity: Int = 0
) {
    init {
        if (location == null && quantity != 0) {
            throw ValidationException("Location must be set when quantity is not zero")
        }
    }

    var location: String = location ?: UNKNOWN_LOCATION
        private set

    var quantity: Int = quantity
        private set

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

        // In the case of over-picking, log it and set the quantity to zero.
        // This is done in the hope that on return the database recovers
        if (amountPicked > itemsInStockQuantity) {
            logger.error {
                "Tried to pick too many items for item with id '$hostId'. " +
                    "WLS DB has '$itemsInStockQuantity' stocked, and storage system tried to pick '$amountPicked'"
            }
        }

        quantity = Math.clamp(itemsInStockQuantity.minus(amountPicked).toLong(), 0, Int.MAX_VALUE)
        if (quantity == 0) {
            location = WITH_LENDER_LOCATION
        }

        return this
    }

    /**
     * Synchronizes the quantity and location of the item.
     * Use it for stock sync updates from storage systems.
     * If the location is null and quantity is not zero, it throws a ValidationException.
     *
     * @param quantity The new quantity to set for the item.
     * @param location The new location to set for the item.
     * @throws ValidationException if the location is null and quantity is not zero.
     */
    @Throws(ValidationException::class)
    fun synchronizeQuantityAndLocation(
        quantity: Int,
        location: String?
    ) {
        if (location == null && quantity != 0) {
            throw ValidationException("Location must be set when quantity is not zero")
        }

        this.quantity = quantity

        // do not override with lender location
        if (location == null && this.location == WITH_LENDER_LOCATION) {
            return
        }

        this.location = location ?: UNKNOWN_LOCATION
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Item) return false

        return other.hostId == hostId && other.hostName == hostName
    }

    override fun hashCode(): Int {
        var result = hostId.hashCode()
        result = 31 * result + hostName.hashCode()
        return result
    }

    override fun toString(): String =
        "Item(hostId='$hostId', hostName=$hostName, description='$description', itemCategory=$itemCategory, preferredEnvironment=" +
            "$preferredEnvironment, packaging=$packaging, callbackUrl=$callbackUrl, location='$location', quantity=$quantity)"
}
