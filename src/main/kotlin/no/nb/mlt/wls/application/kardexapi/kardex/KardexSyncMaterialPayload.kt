package no.nb.mlt.wls.application.kardexapi.kardex

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.inbound.StockCount

data class KardexSyncMaterialPayload(
    @field:Schema(
        description = """The main material ID of the item."""
    )
    val hostId: String,
    @field:Schema(
        description = """Name of the host system which the material belongs to.""",
        example = "AXIELL"
    )
    val hostName: String,
    @field:Schema(
        description = """The current quantity of the material."""
    )
    val quantity: Double,
    @field:Schema(
        description = """Name of the warehouse where the materials is located."""
    )
    val location: String
)

fun List<KardexSyncMaterialPayload>.toStockCountPayload(): List<StockCount.CountStockDTO> =
    this.map { kardexPayload ->
        StockCount.CountStockDTO(
            hostId = kardexPayload.hostId,
            hostName = HostName.fromString(kardexPayload.hostName),
            location = kardexPayload.location,
            quantity = kardexPayload.quantity.toInt(),
            associatedStorage = AssociatedStorage.KARDEX
        )
    }
