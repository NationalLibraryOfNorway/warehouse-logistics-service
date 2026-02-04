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
        "loadUnit" : [
            {
                "productId" : "mlt-12345",
                "productOwner" : "NB",
                "quantityOnHand" : 1.0,
                "hostName" : "Axiell",
                "location" : "SYNQ_WAREHOUSE",
                "productCategory" : "Papir",
                "uom" : "OBJ",
                "confidentialProduct" : false
            }
        ]
    }
    """
)
data class SynqInventoryReconciliationPayload(
    @field:Schema(description = """List of load units in the warehouse""")
    val loadUnit: List<LoadUnit>
)

@Schema(
    description = """Object containing product data needed for inventory reconciliation""",
    example = """
    {
        "productId" : "mlt-12345",
        "productOwner" : "NB",
        "quantityOnHand" : 1.0,
        "hostName" : "Axiell",
        "location" : "SYNQ_WAREHOUSE",
        "productCategory" : "Papir",
        "uom" : "OBJ",
        "confidentialProduct" : false
    }
    """
)
data class LoadUnit(
    @field:Schema(
        description = """ID of the product""",
        example = "mlt-12345"
    )
    val productId: String,
    @field:Schema(
        description = """Owner of the product""",
        examples = ["NB", "AV"]
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
        examples = ["Axiell", "Bibliofil", "Alma", "Asta", "Mellomlager"]
    )
    val hostName: String?,
    @field:Schema(
        description = """Location of the product""",
        example = "SYNQ_WAREHOUSE"
    )
    val location: String?,
    @field:Schema(
        description = """Category of the product""",
        examples = ["Arkivmateriale", "Papir", "Film", "Fotografi", "Sekkepost"]
    )
    val productCategory: String,
    @field:Schema(
        description = """Unit of measure""",
        examples = ["ABOX", "OBJ", "ESK"]
    )
    val uom: String,
    @field:Schema(
        description = """Whether this product is confidential""",
        example = "false"
    )
    val confidentialProduct: Boolean
) {
    @JsonIgnore
    fun getMappedCategory(): ItemCategory =
        when (productCategory.lowercase()) {
            "arkivmateriale", "papir" -> ItemCategory.PAPER
            "film" -> ItemCategory.FILM
            "fotografi" -> ItemCategory.PHOTO
            "sekkepost" -> ItemCategory.BULK_ITEMS
            else -> throw InvalidParameterException("Unknown category: $productCategory")
        }

    @JsonIgnore
    fun getMappedUOM() =
        when (uom.lowercase()) {
            "abox" -> Packaging.ABOX
            "esk" -> Packaging.BOX
            "obj" -> Packaging.NONE
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
            "axiell" -> HostName.AXIELL
            "bibliofil" -> HostName.BIBLIOFIL
            "mellomlager" -> HostName.TEMP_STORAGE
            "unknown" -> HostName.UNKNOWN
            else -> throw InvalidParameterException("Unknown Host Name: $hostName for item: $this")
        }
    }

    @JsonIgnore
    fun mapCurrentPreferredEnvironment(): Environment =
        if (location?.startsWith("ws_frys", ignoreCase = true) == true ||
            productCategory.equals("film", ignoreCase = true)
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
            associatedStorage = computeAssociatedStorage(location ?: ""),
            confidential = confidentialProduct
        )
}
