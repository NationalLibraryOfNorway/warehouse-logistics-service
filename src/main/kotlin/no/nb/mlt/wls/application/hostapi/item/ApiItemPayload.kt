package no.nb.mlt.wls.application.hostapi.item

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.ports.inbound.ItemMetadata
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import org.apache.commons.validator.routines.UrlValidator

@Schema(
    description = """Payload for registering an item in Hermes WLS, and appropriate storage systems.""",
    example = """
    {
      "hostId": "mlt-12345",
      "hostName": "AXIELL",
      "description": "Tyven, tyven skal du hete",
      "itemCategory": "PAPER",
      "preferredEnvironment": "NONE",
      "packaging": "NONE",
      "callbackUrl": "https://callback-wls.no/item"
    }
    """
)
data class ApiItemPayload(
    @Schema(
        description = """The item ID from the host system, usually a barcode or an equivalent ID.""",
        example = "mlt-12345"
    )
    val hostId: String,
    @Schema(
        description = """Name of the host system which the item originates from.
                Host system is usually the catalogue that the item is registered in.""",
        examples = [ "AXIELL", "ALMA", "ASTA", "BIBLIOFIL" ]
    )
    val hostName: HostName,
    @Schema(
        description = """Description of the item for easy identification in the warehouse system.
                Usually an item title/name, e.g. book title, film name, etc. or contents description.""",
        examples = ["Tyven, tyven skal du hete", "Avisa Hemnes", "Kill Buljo"]
    )
    val description: String,
    @Schema(
        description = """Item's category, same category indicates that the items can be stored together without any preservation issues.
                For example: books, magazines, newspapers, etc. are of type PAPER, and can be stored together without damaging each other.""",
        examples = ["PAPER", "DISC", "FILM", "PHOTO", "EQUIPMENT", "BULK_ITEMS", "MAGNETIC_TAPE"]
    )
    val itemCategory: ItemCategory,
    @Schema(
        description = """What kind of environment the item should be stored in.
                "NONE" is for normal storage for the item category, "FRYS" is for frozen storage, etc.
                NOTE: This is not a guarantee that the item will be stored in the preferred environment.
                In cases where storage space is limited, the item may be stored in a different environment.""",
        examples = ["NONE", "FRYS"]
    )
    val preferredEnvironment: Environment,
    @Schema(
        description = """Whether the item is a single object or a container with other items inside.
                "NONE" is for single objects, "ABOX" is for archival boxes, etc.
                NOTE: It is up to the catalogue to keep track of the items inside a container.""",
        examples = ["NONE", "BOX", "ABOX", "CRATE"]
    )
    val packaging: Packaging,
    @Schema(
        description = """Callback URL to use for sending item updates to the host system.
            For example when item moves or changes quantity in storage.""",
        example = "https://callback-wls.no/item"
    )
    val callbackUrl: String?,
    @Schema(
        description = """Where the item is located, can be used for tracking item movement through storage systems.""",
        examples = ["UNKNOWN", "SYNQ_WAREHOUSE", "AUTOSTORE", "KARDEX"],
        required = false
    )
    val location: String?,
    @Schema(
        description = """Quantity on hand of the item, this easily denotes if the item is in the storage or not.
                If the item is in storage then quantity is 1, if it's not in storage then quantity is 0.""",
        examples = [ "0", "1"],
        required = false
    )
    val quantity: Int?
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

    fun toItemMetadata(): ItemMetadata =
        ItemMetadata(
            hostId = hostId,
            hostName = hostName,
            description = description,
            itemCategory = itemCategory,
            preferredEnvironment = preferredEnvironment,
            packaging = packaging,
            callbackUrl = callbackUrl
        )

    @Throws(ValidationException::class)
    fun validate() {
        if (hostId.isBlank()) {
            throw ValidationException("The item's 'hostId' is required, and it cannot be blank")
        }

        if (description.isBlank()) {
            throw ValidationException("The item's 'description' is required, and it cannot be blank")
        }

        if (location != null && location.isBlank()) {
            throw ValidationException("The item's 'location' cannot be blank if set")
        }

        if (quantity != null && quantity != 0 && quantity != 1) {
            throw ValidationException("The item's 'quantity' must be one or zero if set")
        }

        if (callbackUrl != null && !isValidUrl(callbackUrl)) {
            throw ValidationException("The item's 'callback URL' must be valid if set")
        }
    }

    private fun isValidUrl(url: String): Boolean {
        // Yes I am aware that this function is duplicated in three places
        // But I prefer readability to DRY in cases like this

        val validator = UrlValidator(arrayOf("http", "https")) // Allow only HTTP/HTTPS
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
