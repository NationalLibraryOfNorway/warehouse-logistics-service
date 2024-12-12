package no.nb.mlt.wls.infrastructure.callbacks

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.model.Packaging

@Schema(
    description = """Payload for updates about items sent from Hermes WLS to the appropriate catalogues.""",
    example = """
    {
      "hostId": "mlt-12345",
      "hostName": "AXIELL",
      "description": "Tyven, tyven skal du hete",
      "itemCategory": "papir",
      "preferredEnvironment": "NONE",
      "packaging": "NONE",
      "owner": "NB",
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
        description = """Category of the item, indicates that items sharing a category can be stored together.
                For example, books, magazines, newspapers, etc. can be stored together.""",
        examples = ["BOOK", "ISSUE", "Arkivmateriale", "Film_Frys"]
    )
    val itemCategory: String,
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
        description = """Who owns the item. Usually the National Library of Norway ("NB") or the National Archives of Norway ("ARKIVVERKET").""",
        examples = ["NB", "ARKIVVERKET"]
    )
    val owner: Owner,
    @Schema(
        description = """Callback URL to use for sending item updates in the host system.
            For example when item moves or changes quantity in storage.""",
        example = "https://callback-wls.no/item"
    )
    val callbackUrl: String?,
    @Schema(
        description = """Where the item is located, can be used for tracking item movement through storage systems.""",
        examples = ["SYNQ_WAREHOUSE", "AUTOSTORE", "KARDEX"],
        accessMode = READ_ONLY,
        required = false
    )
    val location: String?,
    @Schema(
        description = """Quantity on hand of the item, this easily denotes if the item is in the storage or not.
                If the item is in storage then quantity is 1, if it's not in storage then quantity is 0.""",
        examples = [ "0.0", "1.0"],
        accessMode = READ_ONLY,
        required = false
    )
    val quantity: Int?
)

fun NotificationItemPayload.toItem(): Item {
    return Item(
        hostId = hostId,
        hostName = hostName,
        description = description,
        itemCategory = itemCategory,
        preferredEnvironment = preferredEnvironment,
        packaging = packaging,
        owner = owner,
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
        owner = owner,
        callbackUrl = callbackUrl,
        location = location,
        quantity = quantity
    )
}
