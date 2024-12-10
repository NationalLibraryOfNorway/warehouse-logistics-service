package no.nb.mlt.wls.application.hostapi.item

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.model.Packaging
import no.nb.mlt.wls.domain.ports.inbound.ItemMetadata
import no.nb.mlt.wls.domain.ports.inbound.ValidationException
import java.net.URI

@Schema(
    description = "Payload for registering an item in Hermes WLS, and appropriate storage system for the item.",
    example = """
    {
      "hostId": "mlt-12345",
      "hostName": "AXIELL",
      "description": "Tyven, tyven skal du hete",
      "itemCategory": "BOOK",
      "preferredEnvironment": "NONE",
      "packaging": "NONE",
      "owner": "NB"
    }
    """
)
data class ApiItemPayload(
    @Schema(
        description = "The item ID from the host system, usually a barcode or an equivalent ID.",
        example = "mlt-12345"
    )
    val hostId: String,
    @Schema(
        description =
            "Name of the host system which the item originates from. " +
                "Host system is usually the catalogue that the item is registered in.",
        examples = [ "AXIELL", "ALMA", "ASTA", "BIBLIOFIL" ]
    )
    val hostName: HostName,
    @Schema(
        description =
            "Description of the item for easy identification in the warehouse system. " +
                "Usually an item title/name, e.g. book title, film name, etc. or contents description.",
        examples = ["Tyven, tyven skal du hete", "Avisa Hemnes", "Kill Buljo"]
    )
    val description: String,
    @Schema(
        description = "What kind of item category the item belongs to, e.g. Books, Issues, Films, etc.",
        examples = [
            "safetyfilm", "nitratfilm", "film", "plater", "fotografier", "papir",
            "gjenstand", "lydbånd", "videobånd", "sekkepost", "magnetbånd"
        ]
    )
    val itemCategory: ItemCategory,
    @Schema(
        description = "What kind of environment the item should be stored in, e.g. NONE, FRYS, MUGG_CELLE, etc.",
        examples = ["NONE", "FRYS"]
    )
    val preferredEnvironment: Environment,
    @Schema(
        description =
            "Whether the item is a single object or a box/abox/crate of other items. " +
                "NONE is for single objects, BOX is for boxes, ABOX is for archival boxes, and CRATE is for crates.",
        examples = ["NONE", "BOX", "ABOX", "CRATE"]
    )
    val packaging: Packaging,
    @Schema(
        description = "Who owns the item. Usually the National Library of Norway (NB) or the National Archives of Norway (ARKIVVERKET).",
        examples = ["NB", "ARKIVVERKET"]
    )
    val owner: Owner,
    @Schema(
        description = "Callback URL for the item used to update the item information in the host system.",
        example = "https://example.com/send/callback/here"
    )
    val callbackUrl: String?,
    @Schema(
        description = "Where the item is located, e.g. SYNQ_WAREHOUSE, AUTOSTORE, KARDEX, etc.",
        examples = ["SYNQ_WAREHOUSE", "AUTOSTORE", "KARDEX"],
        accessMode = READ_ONLY,
        required = false
    )
    val location: String?,
    @Schema(
        description =
            "Quantity on hand of the item, this denotes if the item is in storage or not. " +
                "If the item is in storage then quantity is at least 1, if it's not in storage then quantity is 0.",
        examples = [ "0", "1"],
        accessMode = READ_ONLY,
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
            owner = owner,
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
            owner = owner,
            callbackUrl = callbackUrl
        )

    @Throws(ValidationException::class)
    fun validate() {
        if (hostId.isBlank()) {
            throw ValidationException("The item's hostId is required, and it cannot be blank")
        }

        if (description.isBlank()) {
            throw ValidationException("The item's description is required, and it cannot be blank")
        }

        if (location != null && location.isBlank()) {
            throw ValidationException("The item's location cannot be blank if set")
        }

        if (quantity != null && quantity < 0.0) {
            throw ValidationException("The item's quantity must be positive or zero if set")
        }

        if (callbackUrl != null && !isValidUrl(callbackUrl)) {
            throw ValidationException("The item's callback URL must be valid if set")
        }
    }

    private fun isValidUrl(url: String): Boolean {
        // Yes I am aware that this function is duplicated in three places
        // But I prefer readability over DRY in cases like this

        return try {
            URI(url).toURL() // Try to create a URL object
            true
        } catch (_: Exception) {
            false // If exception is thrown, it's not a valid URL
        }
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
        owner = owner,
        callbackUrl = callbackUrl,
        location = location,
        quantity = quantity
    )
