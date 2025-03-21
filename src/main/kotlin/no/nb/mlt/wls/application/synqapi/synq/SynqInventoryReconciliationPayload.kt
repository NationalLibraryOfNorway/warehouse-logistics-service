package no.nb.mlt.wls.application.synqapi.synq

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = """Payload for inventory reconciliation""",
    example = """
    {
        "warehouse" : "Sikringsmagasin_2",
        "loadUnit" : [
            {
                "productId" : "001a72b4-19bf-4371-8b47-76caa273fc52",
                "productOwner" : "AV",
                "quantityOnHand" : 1.0,
                "hostName" : "Asta",
                "confidentalProduct" : false
            }
        ]
    }"""
)
data class SynqInventoryReconciliationPayload(
    @Schema(description = """List of load units in the warehouse""")
    val loadUnit: List<LoadUnit>
)

data class LoadUnit(
    @Schema(
        description = """ID of the product""",
        example = "001a72b4-19bf-4371-8b47-76caa273fc52"
    )
    val productId: String,
    @Schema(
        description = """Owner of the product""",
        example = "AV"
    )
    val productOwner: String,
    @Schema(
        description = """Quantity of the product""",
        example = "1.0"
    )
    val quantityOnHand: Double,
    @Schema(
        description = """Which host system the product belongs to""",
        example = "Asta"
    )
    val hostName: String?,
    @Schema(
        description = """Location of the product""",
        example = "SYNQ_WAREHOUSE"
    )
    val location: String
)
