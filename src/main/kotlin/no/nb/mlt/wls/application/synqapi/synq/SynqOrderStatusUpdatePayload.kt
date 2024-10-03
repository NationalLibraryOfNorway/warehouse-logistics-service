package no.nb.mlt.wls.application.synqapi.synq

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Payload for updating the status of an order placed in SynQ.",
    example = """
    {
      "prevStatus": "PICKED",
      "status": "COMPLETED",
      "hostName" : "Axiell",
      "warehouse" : "Sikringmagasin_2"
    }"""
)
data class SynqOrderStatusUpdatePayload(
    @Schema(
        description = "Previous status of the order in SynQ.",
        example = "PICKED"
    )
    val prevStatus: String,
    @Schema(
        description = "Current status of the order in SynQ.",
        example = "COMPLETED"
    )
    val status: String,
    @Schema(
        description = "Name of the host system which placed the order/owns the order products.",
        example = "Axiell"
    )
    val hostName: String,
    @Schema(
        description = "Name of the warehouse where the order products/items are located.",
        example = "Sikringmagasin_2"
    )
    val warehouse: String
)
