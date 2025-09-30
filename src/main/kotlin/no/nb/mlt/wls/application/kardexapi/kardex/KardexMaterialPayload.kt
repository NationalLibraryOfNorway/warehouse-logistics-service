package no.nb.mlt.wls.application.kardexapi.kardex

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero

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
data class KardexMaterialPayload(
    @field:Schema(
        description = """Name of the host system which the material belongs to.""",
        example = "AXIELL"
    )
    override val hostName: String,
    @field:Schema(
        description = """The main material ID of the item."""
    )
    @field:NotBlank(message = "Item ID can not be blank")
    override val hostId: String,
    @field:Schema(
        description = """The current quantity of the item."""
    )
    @field:PositiveOrZero(message = "Item quantity must be positive")
    override val quantity: Double,
    @field:Schema(
        description = """Name of the warehouse where the order materials/items are located."""
    )
    override val location: String,
    @field:Schema(
        description = """The name of the person who updated/operated on the Kardex system."""
    )
    override val operator: String,
    @field:Schema(
        description = """Describes the motive for the update message, such as deletion."""
    )
    override val motiveType: MotiveType
) : KardexItemPayload
