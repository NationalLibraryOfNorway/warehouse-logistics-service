package no.nb.mlt.wls.application.kardexapi.kardex

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.inbound.UpdateItem

@Schema(
    description = "Payload with updates status for material in Kardex.",
    example = """
    {
      "hostName": "AXIELL",
      "hostId": "CM00012345",
      "quantity": 1.0,
      "location": "NB Mo i Rana",
      "operator": "Ola Nordmann",
      "motiveType": 0
    }"""
)
data class KardexMaterialUpdatePayload(
    @field:Schema(
        description = """The main material ID of the item."""
    )
    val hostId: String,
    @field:Schema(
        description = """Name of the host system which the material belongs to.""",
        example = "AXIELL"
    )
    val hostName: HostName?,
    @field:Schema(
        description = """The current quantity of the item."""
    )
    val quantity: Double,
    @field:Schema(
        description = """Name of the warehouse where the order materials/items are located."""
    )
    @field:NotBlank(message = "Order status update cannot have blank warehouse")
    val location: String,
    @field:Schema(
        description = """The name of the person who updated/operated on the Kardex system."""
    )
    val operator: String,
    val motiveType: MotiveType
) {
    fun toUpdateItemPayload(): UpdateItem.UpdateItemPayload =
        UpdateItem.UpdateItemPayload(
            hostName = hostName ?: HostName.NONE,
            hostId = hostId,
            quantity = quantity.toInt(),
            location = location
        )
}
