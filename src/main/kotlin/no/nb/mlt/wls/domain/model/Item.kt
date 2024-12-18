package no.nb.mlt.wls.domain.model

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

data class Item(
    val hostId: String,
    val hostName: HostName,
    val description: String,
    val itemCategory: ItemCategory,
    val preferredEnvironment: Environment,
    val packaging: Packaging,
    val callbackUrl: String?,
    val location: String?,
    val quantity: Int?
) {
    fun pickItem(amountPicked: Int): Item {
        val itemsInStockQuantity = quantity ?: 0

        // In the case of over-picking, log it and set quantity to zero.
        // This is in hope that on return the database recovers
        if (amountPicked > itemsInStockQuantity) {
            logger.error {
                "Tried to pick too many items for item with id '$hostId'. " +
                    "WLS DB has '$itemsInStockQuantity' stocked, and storage system tried to pick '$amountPicked'"
            }
        }
        val quantity = Math.clamp(itemsInStockQuantity.minus(amountPicked).toLong(), 0, Int.MAX_VALUE)

        val location: String =
            if (quantity == 0) {
                "WITH_LENDER"
            } else if (location != null) {
                location
            } else {
                // Rare edge case. Log it until we can determine if this actually happens in production
                logger.error {
                    "Item with ID '$hostId' for host '$hostName' without a location was picked. Location was set to 'UNKNOWN'."
                }
                "UNKNOWN"
            }
        return this.copy(quantity = quantity, location = location)
    }
}
