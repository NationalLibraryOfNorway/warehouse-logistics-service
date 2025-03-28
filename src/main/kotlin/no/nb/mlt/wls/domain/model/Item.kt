package no.nb.mlt.wls.domain.model

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.ports.inbound.ValidationException

private val logger = KotlinLogging.logger {}

const val WITH_LENDER_LOCATION = "WITH_LENDER"
const val UNKNOWN_LOCATION = "UNKNOWN"

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

    fun pickItem(amountPicked: Int): Item {
        val itemsInStockQuantity = quantity

        // In the case of over-picking, log it and set quantity to zero.
        // This is in hope that on return the database recovers
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

    fun synchronizeQuantityAndLocation(
        quantity: Int,
        location: String?
    ) {
        if (location == null && quantity != 0) {
            throw ValidationException("Location must be set when quantity is not zero")
        }

        this.quantity = quantity
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
}
