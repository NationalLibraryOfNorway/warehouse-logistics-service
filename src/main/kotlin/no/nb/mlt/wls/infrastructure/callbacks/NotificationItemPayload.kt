package no.nb.mlt.wls.infrastructure.callbacks

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import io.swagger.v3.oas.annotations.media.Schema
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Packaging
import java.time.Instant

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
      "quantity": 1,
      "eventTimestamp": "2025-03-21T20:30:00.000Z",
      "messageId": "123e4567-e89b-12d3-a456-426614174000"
    }
    """
)
data class NotificationItemPayload(
    @Schema(
        description = """The item ID from the host system, usually a barcode or any equivalent ID.""",
        example = "item-12345"
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
        example = "https://callback-wls.no/item"
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
    val quantity: Int,
    @Schema(
        description = """Time at which Hermes WLS received item update from storage system""",
        example = "2025-03-21T20:30:00.000Z"
    )
    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = CustomInstantDeserializer::class)
    val eventTimestamp: Instant,
    @Schema(
        description = """This messages unique ID in UUID format, allows deduplication""",
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    val messageId: String
)

fun NotificationItemPayload.toItem(): Item =
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

fun Item.toNotificationItemPayload(
    eventTimestamp: Instant,
    messageId: String
): NotificationItemPayload =
    NotificationItemPayload(
        hostId = hostId,
        hostName = hostName,
        description = description,
        itemCategory = itemCategory,
        preferredEnvironment = preferredEnvironment,
        packaging = packaging,
        callbackUrl = callbackUrl,
        location = location,
        quantity = quantity,
        eventTimestamp = eventTimestamp,
        messageId = messageId
    )
