package no.nb.mlt.wls.domain.model

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

const val WITH_LENDER_LOCATION = "WITH_LENDER"
const val UNKNOWN_LOCATION = "UNKNOWN"

data class Item(
    val hostId: String,
    val hostName: HostName,
    val description: String,
    val itemCategory: ItemCategory,
    val preferredEnvironment: Environment,
    val packaging: Packaging,
    val callbackUrl: String?,
    var location: String,
    var quantity: Int
) {
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
        val quantity = Math.clamp(itemsInStockQuantity.minus(amountPicked).toLong(), 0, Int.MAX_VALUE)

        this.setQuantity(quantity)
        if (quantity == 0) {
            this.setLocation(WITH_LENDER_LOCATION)
        }
        return this
    }

    fun setLocation(location: String): Item {
        this.location = location
        return this
    }

    fun setQuantity(quantity: Int): Item {
        this.quantity = quantity
        if (quantity == 0) {
            this.location = UNKNOWN_LOCATION
        }

        return this
    }
}
