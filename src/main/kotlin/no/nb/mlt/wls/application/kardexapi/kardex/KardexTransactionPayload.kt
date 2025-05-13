package no.nb.mlt.wls.application.kardexapi.kardex

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.ports.inbound.UpdateItem
import java.time.Instant

@Schema(
    description = "Payload used to handle transactions related to orders from Kardex.",
    example = """
    {
      "masterOrderId": "AXIELL---67e3b5c9f7452461125bb60a",
      "hostName": "AXIELL",
      "material": "CM00012345",
      "quantity": 1.0,
      "warehouse": "NB Mo i Rana",
      "createdDate": "2024-11-08T19:12:00.000Z",
      "operator": "Ola Nordmann",
      "isOrderComplete": false
    }"""
)
data class KardexTransactionPayload(
    @Schema(
        description = """Order ID in Kardex."""
    )
    val masterOrderId: String,
    @Schema(
        description = """Name of the host system which the material belongs to.""",
        example = "AXIELL, ASTA"
    )
    val hostName: HostName,
    @Schema(
        description = """The main material ID of the item."""
    )
    val material: String,
    @Schema(
        description = """The current quantity of the item."""
    )
    val quantity: Double,
    @Schema(
        description = """Name of the warehouse where the order materials/items are located."""
    )
    val warehouse: String,
    @Schema(
        description = """The date when this order was created."""
    )
    val createdDate: Instant,
    @Schema(
        description = """The name of the person who updated/operated on the Kardex system."""
    )
    val operator: String
) {
    fun mapToOrderItems(): List<String> {
        return listOf(material)
    }

    fun toUpdateItemPayload(): UpdateItem.UpdateItemPayload {
        return UpdateItem.UpdateItemPayload(
            hostName = hostName,
            hostId = material,
            quantity = quantity.toInt(),
            location = warehouse
        )
    }
}
