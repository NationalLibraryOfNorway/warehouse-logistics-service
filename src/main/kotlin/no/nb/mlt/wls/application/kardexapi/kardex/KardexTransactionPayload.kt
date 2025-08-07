package no.nb.mlt.wls.application.kardexapi.kardex

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
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
    @field:Schema(
        description = """Order ID in Kardex."""
    )
    @field:NotBlank(message = "Order ID can not be blank")
    val hostOrderId: String,
    @field:Schema(
        description = """Name of the host system which the material belongs to.""",
        example = "AXIELL, ASTA"
    )
    val hostName: HostName,
    @field:Schema(
        description = """The main material ID of the item."""
    )
    @field:NotBlank(message = "Item ID can not be blank")
    val hostId: String,
    @field:Schema(
        description = """The current quantity of the item."""
    )
    @field:PositiveOrZero(message = "Item quantity must be positive")
    val quantity: Double,
    val motiveType: MotiveType,
    @field:Schema(
        description = """Name of the warehouse where the order materials/items are located."""
    )
    @field:NotBlank(message = "Location can not be blank")
    val location: String,
    @field:Schema(
        description = """The name of the person who updated/operated on the Kardex system."""
    )
    val operator: String
) {
    fun mapToOrderItems(): List<String> = listOf(hostId)
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
