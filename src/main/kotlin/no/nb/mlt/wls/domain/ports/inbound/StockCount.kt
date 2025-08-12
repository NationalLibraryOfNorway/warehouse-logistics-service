package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName

/**
 * A functional interface representing the stock counting operation within a storage system.
 *
 * Implementations of this interface are responsible for performing the business logic
 * associated with counting items in stock. Given values should be treated as truth, and so
 * they should take precedence over info stored elsewhere.
 *
 * The method accepts a list of [CountStockDTO] instances, each of which encapsulates information
 * about an item, such as the host it belongs to, the location of the item in storage,
 * and the current quantity on hand.
 *
 * @see CountStockDTO
 */
fun interface StockCount {
    suspend fun countStock(items: List<CountStockDTO>)

    /**
     * This object encapsulates the necessary details for counting stock.
     *
     * This DTO is used for synchronizing stock information from storage with WLS.
     *
     * @property hostId A unique identifier for the host system managing the item.
     * @property hostName The name of the host system, represented as a value from the [HostName] enum.
     * @property location The storage location of the item, expressed as a string.
     * @property quantity The current count of the item in stock.
     */
    data class CountStockDTO(
        val hostId: String,
        val hostName: HostName,
        val location: String,
        val quantity: Int
    )
}
