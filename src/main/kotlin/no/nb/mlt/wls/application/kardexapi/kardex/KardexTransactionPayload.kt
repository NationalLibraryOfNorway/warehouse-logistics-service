package no.nb.mlt.wls.application.kardexapi.kardex

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.HostName

@Schema(
    description = "Payload used to handle transactions related to orders from Kardex.",
    example = """
    {
      "hostOrderId": "AXIELL-KD---67e3b5c9f7452461125bb60a",
      "hostName": "AXIELL",
      "hostId": "CM00012345",
      "quantity": 1.0,
      "motiveType": 0,
      "location": "NB Mo i Rana",
      "operator": "Ola Nordmann"
    }"""
)
data class KardexTransactionPayload(
    @Schema(
        description = """Order ID in Kardex."""
    )
    val hostOrderId: String,
    @Schema(
        description = """Name of the host system which the material belongs to.""",
        example = "AXIELL, ASTA"
    )
    val hostName: HostName,
    @Schema(
        description = """The main material ID of the item."""
    )
    val hostId: String,
    @Schema(
        description = """The current quantity of the item."""
    )
    val quantity: Double,
    val motiveType: MotiveType,
    @Schema(
        description = """Name of the warehouse where the order materials/items are located."""
    )
    val location: String,
    @Schema(
        description = """The name of the person who updated/operated on the Kardex system."""
    )
    val operator: String
) {
    fun mapToOrderItems(): List<String> {
        return listOf(hostId)
    }

    fun mapToItemsPickedMap(): Map<String, Int> = mutableMapOf(this.hostId to this.quantity.toInt())
}

enum class MotiveType {
    NotSet,
    StockUnavailable,
    Shortage,
    SpaceUnavailable,
    SpaceFull,
    Deleted,
    Canceled
}
