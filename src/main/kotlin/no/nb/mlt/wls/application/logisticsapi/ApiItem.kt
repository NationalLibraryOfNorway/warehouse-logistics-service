package no.nb.mlt.wls.application.logisticsapi

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging

data class ApiItem(
    @field:Schema(
        description = """The item ID from the host system, usually a barcode or any equivalent ID.""",
        example = "mlt-12345"
    )
    val hostId: String,
    @field:Schema(
        description = """Name of the host system that owns the item, and where the request comes from.
            Host system is usually the catalogue that the item is registered in.""",
        examples = ["AXIELL", "ALMA", "ASTA", "BIBLIOFIL"]
    )
    val hostName: HostName,
    @field:Schema(
        description = """Description of the item for easy identification in the warehouse system.
            Usually item's title/name, or contents description.""",
        examples = ["Tyven, tyven skal du hete", "Avisa Hemnes", "Kill Buljo", "Photo Collection, Hemnes, 2025-03-12"]
    )
    val description: String,
    @field:Schema(
        description = """Item's storage category or grouping.
            Items sharing a category can be stored together without any issues.
            For example: books and photo positives are of type PAPER, and can be stored without damaging each other.""",
        examples = ["PAPER", "DISC", "FILM", "PHOTO", "EQUIPMENT", "BULK_ITEMS", "MAGNETIC_TAPE"]
    )
    val itemCategory: ItemCategory,
    @field:Schema(
        description = """What kind of environment the item should be stored in.
            "NONE" means item has no preference for storage environment,
            "FREEZE" means that item should be stored frozen when stored, etc.
            NOTE: This is not a guarantee that the item will be stored in the preferred environment.
            In cases where storage space is limited, the item may be stored in regular environment.""",
        examples = ["NONE", "FREEZE"]
    )
    val preferredEnvironment: Environment,
    @field:Schema(
        description = """Whether the item is a single object or a container with other items inside.
            "NONE" is for single objects, "ABOX" is for archival boxes, etc.
            NOTE: It is up to the catalogue to keep track of the items inside a container.""",
        examples = ["NONE", "BOX", "ABOX"]
    )
    val packaging: Packaging,
    @field:Schema(
        description = """Last known storage location of the item.
            Can be used for tracking item movement through storage systems.""",
        examples = ["UNKNOWN", "WITH_LENDER", "SYNQ_WAREHOUSE", "AUTOSTORE", "KARDEX"]
    )
    val location: String,
    @field:Schema(
        description = """Quantity on hand of the item, this denotes if the item is in the storage or not.
            Quantity of 1 means the item is in storage, quantity of 0 means the item is not in storage.""",
        examples = ["0", "1"]
    )
    val quantity: Int
)

fun Item.toApiItem(): ApiItem =
    ApiItem(
        hostId = this.hostId,
        hostName = this.hostName,
        description = this.description,
        itemCategory = this.itemCategory,
        preferredEnvironment = this.preferredEnvironment,
        packaging = this.packaging,
        location = this.location,
        quantity = this.quantity
    )
