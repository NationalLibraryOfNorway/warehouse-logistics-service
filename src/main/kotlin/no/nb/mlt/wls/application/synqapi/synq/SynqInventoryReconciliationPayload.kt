package no.nb.mlt.wls.application.synqapi.synq

import com.fasterxml.jackson.annotation.JsonIgnore
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.ports.inbound.SynchronizeItems
import java.security.InvalidParameterException

private val logger = KotlinLogging.logger {}

@Schema(
    description = """Payload for inventory reconciliation""",
    example = """
    {
        "warehouse" : "Sikringsmagasin_2",
        "loadUnit" : [
            {
                "productId" : "mlt-12345",
                "productOwner" : "NB",
                "quantityOnHand" : 1.0,
                "hostName" : "Axiell",
                "confidentalProduct" : false
            }
        ]
    }"""
)
data class SynqInventoryReconciliationPayload(
    @field:Schema(description = """List of load units in the warehouse""")
    val loadUnit: List<LoadUnit>
)

data class LoadUnit(
    @field:Schema(
        description = """ID of the product""",
        example = "mlt-12345"
    )
    val productId: String,
    @field:Schema(
        description = """Owner of the product""",
        example = "NB"
    )
    val productOwner: String,
    @field:Schema(description = """Description of the product""")
    val description: String?,
    @field:Schema(
        description = """Quantity of the product""",
        example = "1.0"
    )
    val quantityOnHand: Double,
    @field:Schema(
        description = """Which host system the product belongs to""",
        example = "Axiell"
    )
    val hostName: String?,
    @field:Schema(
        description = """Location of the product""",
        example = "SYNQ_WAREHOUSE"
    )
    val location: String?,
    @field:Schema(
        description = """Category of the product""",
        example = "PAPER"
    )
    val productCategory: String,
    @field:Schema(
        description = """Unit of measure""",
        example = "ESK"
    )
    val uom: String
) {
    @JsonIgnore
    fun getMappedCategory(): ItemCategory {
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

    @JsonIgnore
    fun getMappedUOM() =
        when (uom) {
            "ABOX" -> Packaging.ABOX
            "ESK" -> Packaging.BOX
            "OBJ" -> Packaging.NONE
            else -> throw InvalidParameterException("Unknown UOM: $uom")
        }

    @JsonIgnore
    fun getMappedHostName(): HostName {
        if (hostName.isNullOrBlank()) {
            logger.warn { "Got a LoadUnit with blank or null Host Name: $this" }
            return HostName.UNKNOWN
        }

        return when (hostName.lowercase()) {
            "alma" -> HostName.ALMA
            "asta" -> HostName.ASTA
            "mavis" -> HostName.AXIELL // This needs to be changed when we have fixed SynQ to use Axiell instead of Mavis
            "axiell" -> HostName.AXIELL
            "mellomlager" -> HostName.TEMP_STORAGE
            else -> throw InvalidParameterException("Unknown Host Name: $hostName for item: $this")
        }
    }

    @JsonIgnore
    fun mapCurrentPreferredEnvironment(): Environment =
        if (location?.startsWith("WS_FRYS", ignoreCase = true) == true ||
            productCategory.contains("frys", ignoreCase = true)
        ) {
            Environment.FREEZE
        } else {
            Environment.NONE
        }

    @JsonIgnore
    fun toItemToSynchronize(): SynchronizeItems.ItemToSynchronize =
        SynchronizeItems.ItemToSynchronize(
            hostId = productId,
            hostName = getMappedHostName(),
            location = location,
            quantity = quantityOnHand.toInt(),
            itemCategory = getMappedCategory(),
            packaging = getMappedUOM(),
            currentPreferredEnvironment = mapCurrentPreferredEnvironment(),
            description = if (description.isNullOrBlank()) "-" else description,
            associatedStorage = computeAssociatedStorage(location ?: "")
        )
}
