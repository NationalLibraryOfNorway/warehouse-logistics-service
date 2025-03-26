package no.nb.mlt.wls.application.synqapi.synq

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging
import java.security.InvalidParameterException

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
    @Schema(description = """Description of the product""")
    val description: String?,
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
    val location: String?,
    @Schema(
        description = """Category of the product""",
        example = "PAPER"
    )
    val productCategory: String,
    @Schema(
        description = "Unit of measure",
        example = "ESK"
    )
    val uom: String
)

fun LoadUnit.getMappedCategory(): ItemCategory {
    if (productCategory.startsWith("arkiv", ignoreCase = true)) return ItemCategory.PAPER

    return when (productCategory.lowercase()) {
        "systemtest", "helsemateriale", "aviser", "book", "issue", "papir" -> ItemCategory.PAPER
        "film_frys", "film" -> ItemCategory.FILM
        "fotografi", "fotografi_frys" -> ItemCategory.PHOTO
        "abm" -> ItemCategory.EQUIPMENT
        "sekkepost" -> ItemCategory.BULK_ITEMS
        "plate" -> ItemCategory.DISC
        "gjenstand" -> ItemCategory.EQUIPMENT
        "magnetbånd" -> ItemCategory.MAGNETIC_TAPE

        else -> throw InvalidParameterException("Unknown category: $productCategory")
    }
}

fun LoadUnit.getMappedUOM() =
    when (uom) {
        "ABOX" -> Packaging.ABOX
        "ESK" -> Packaging.BOX
        "OBJ" -> Packaging.NONE
        else -> throw InvalidParameterException("Unknown UOM: $uom")
    }

fun LoadUnit.getMappedHostName(): HostName? {
    if (hostName.isNullOrBlank()) {
        return HostName.NONE
    }

    return when (hostName.lowercase()) {
        "alma" -> HostName.ALMA
        "asta" -> HostName.ASTA
        "mavis" -> HostName.MAVIS
        "axiell" -> HostName.AXIELL
        else -> null
    }
}

fun LoadUnit.mapCurrentPreferredEnvironment(): Environment {
    return if (location?.startsWith("WS_FRYS", ignoreCase = true) == true ||
        productCategory.contains("frys", ignoreCase = true)
    ) {
        Environment.FREEZE
    } else {
        Environment.NONE
    }
}
