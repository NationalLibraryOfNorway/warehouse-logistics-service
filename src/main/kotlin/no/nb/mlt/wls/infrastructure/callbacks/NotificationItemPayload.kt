package no.nb.mlt.wls.infrastructure.callbacks

import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging

@Schema(
    description = """Payload for updates about items sent from Hermes WLS to the appropriate catalogues.""",
    example = """
    {
      "hostId": "mlt-12345",
      "hostName": "AXIELL",
      "description": "Tyven, tyven skal du hete",
      "itemCategory": "PAPER",
      "preferredEnvironment": "NONE",
      "packaging": "NONE",
      "callbackUrl": "https://callback-wls.no/item",
      "location": "SYNQ_WAREHOUSE",
      "quantity": 1
    }
    """
)
data class NotificationItemPayload(
    @Schema(
        description = """The item ID from the host system, usually a barcode or an equivalent ID.""",
        example = "item-12345"
    )
    val hostId: String,
    @Schema(
        description = """Name of the host system which the item originates from.
                Host system is usually the catalogue that the item is registered in.""",
        examples = ["AXIELL", "ALMA", "ASTA", "BIBLIOFIL"]
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
                "NONE" is for normal storage for the item category, "FREEZE" is for frozen storage, etc.
                NOTE: This is not a guarantee that the item will be stored in the preferred environment.
                In cases where storage space is limited, the item may be stored in a different environment.""",
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
        description = """Callback URL to use for sending item updates to the host system.
            For example when item moves or changes quantity in storage.""",
        example = "https://callback-wls.no/item"
    )
    val callbackUrl: String?,
    @Schema(
        description = """Where the item is located, can be used for tracking item movement through storage systems.""",
        examples = ["UNKNOWN", "WITH_LENDER", "SYNQ_WAREHOUSE", "AUTOSTORE", "KARDEX"],
        required = false
    )
    val location: String,
    @Schema(
        description = """Quantity on hand of the item, this easily denotes if the item is in the storage or not.
                If the item is in storage then quantity is 1, if it's not in storage then quantity is 0.""",
        examples = [ "0.0", "1.0"]
    )
    val quantity: Int
)

fun NotificationItemPayload.toItem(): Item {
    return Item(
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
}

fun Item.toNotificationItemPayload(): NotificationItemPayload {
    return NotificationItemPayload(
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
}
