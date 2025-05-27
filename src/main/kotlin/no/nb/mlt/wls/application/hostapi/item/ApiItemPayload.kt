package no.nb.mlt.wls.application.hostapi.item

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import org.apache.commons.validator.routines.UrlValidator

@Schema(
    description = """Payload representing an item in Hermes WLS, and appropriate storage systems.""",
    example = """
    {
      "hostId": "mlt-12345",
      "hostName": "AXIELL",
      "description": "Tyven, tyven skal du hete",
      "itemCategory": "PAPER",
      "preferredEnvironment": "NONE",
      "packaging": "NONE",
      "callbackUrl": "http://callback-wls.no/item",
      "location": "SYNQ_WAREHOUSE",
      "quantity": 1
    }
    """
)
data class ApiItemPayload(
    @Schema(
        description = """The item ID from the host system, usually a barcode or any equivalent ID.""",
        example = "mlt-12345"
    )
    val hostId: String,
    @Schema(
        description = """Name of the host system that owns the item, and where the request comes from.
            Host system is usually the catalogue that the item is registered in.""",
        examples = ["AXIELL", "ALMA", "ASTA", "BIBLIOFIL"]
    )
    val hostName: HostName,
    @Schema(
        description = """Description of the item for easy identification in the warehouse system.
            Usually item's title/name, or contents description.""",
        examples = ["Tyven, tyven skal du hete", "Avisa Hemnes", "Kill Buljo", "Photo Collection, Hemnes, 2025-03-12"]
    )
    val description: String,
    @Schema(
        description = """Item's storage category or grouping.
            Items sharing a category can be stored together without any issues.
            For example: books and photo positives are of type PAPER, and can be stored without damaging each other.""",
        examples = ["PAPER", "DISC", "FILM", "PHOTO", "EQUIPMENT", "BULK_ITEMS", "MAGNETIC_TAPE"]
    )
    val itemCategory: ItemCategory,
    @Schema(
        description = """What kind of environment the item should be stored in.
            "NONE" means item has no preference for storage environment,
            "FREEZE" means that item should be stored frozen when stored, etc.
            NOTE: This is not a guarantee that the item will be stored in the preferred environment.
            In cases where storage space is limited, the item may be stored in regular environment.""",
        examples = ["NONE", "FREEZE"]
    )
    val preferredEnvironment: Environment,
    @Schema(
        description = """Whether the item is a single object or a container with other items inside.
            "NONE" is for single objects, "ABOX" is for archival boxes, etc.
            NOTE: It is up to the catalogue to keep track of the items inside a container.""",
        examples = ["NONE", "BOX", "ABOX"]
    )
    val packaging: Packaging,
    @Schema(
        description = """This URL will be used for POSTing item updates to the host system.
            For example when item moves or changes quantity in storage.""",
        example = "http://callback-wls.no/item"
    )
    val callbackUrl: String?,
    @Schema(
        description = """Last known storage location of the item.
            Can be used for tracking item movement through storage systems.""",
        examples = ["UNKNOWN", "WITH_LENDER", "SYNQ_WAREHOUSE", "AUTOSTORE", "KARDEX"]
    )
    val location: String,
    @Schema(
        description = """Quantity on hand of the item, this denotes if the item is in the storage or not.
            Quantity of 1 means the item is in storage, quantity of 0 means the item is not in storage.""",
        examples = ["0", "1"]
    )
    val quantity: Int
) {
    fun toItem(): Item =
        Item(
            hostId = hostId,
            hostName = hostName,
            description = description,
            itemCategory = itemCategory,
            preferredEnvironment = preferredEnvironment,
            packaging = packaging,
            callbackUrl = callbackUrl,
            location = location,
            quantity = quantity
        )

    @Throws(ValidationException::class)
    fun validate() {
        if (hostId.isBlank()) {
            throw ValidationException("The item's 'hostId' is required, and it cannot be blank")
        }

        if (description.isBlank()) {
            throw ValidationException("The item's 'description' is required, and it cannot be blank")
        }

        if (location.isBlank()) {
            throw ValidationException("The item's 'location' is required, and it cannot be blank")
        }

        if (quantity != 0 && quantity != 1) {
            throw ValidationException("The item's 'quantity' must be one or zero")
        }

        if (callbackUrl != null && !isValidUrl(callbackUrl)) {
            throw ValidationException("The item's 'callback URL' must be valid if set")
        }
    }

    private fun isValidUrl(url: String): Boolean {
        val validator = UrlValidator(arrayOf("http", "https"))
        return validator.isValid(url)
    }
}

fun Item.toApiPayload() =
    ApiItemPayload(
        hostId = hostId,
        hostName = hostName,
        description = description,
        itemCategory = itemCategory,
        preferredEnvironment = preferredEnvironment,
        packaging = packaging,
        callbackUrl = callbackUrl,
        location = location,
        quantity = quantity
    )
