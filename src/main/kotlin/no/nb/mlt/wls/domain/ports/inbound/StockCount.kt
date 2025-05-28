package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName

fun interface StockCount {
    suspend fun countStock(items: List<CountStockDTO>)

    data class CountStockDTO(
        val hostId: String,
        val hostName: HostName,
        val location: String,
        val quantity: Int
    )
}
