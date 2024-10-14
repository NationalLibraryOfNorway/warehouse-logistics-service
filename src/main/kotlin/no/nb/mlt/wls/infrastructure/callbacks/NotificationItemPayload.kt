package no.nb.mlt.wls.infrastructure.callbacks

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.model.Packaging

data class NotificationItemPayload(
    @Schema(
        description = "ID from the host system which owns the item.",
        examples = ["item-12345"]
    )
    val hostId: String,
    @Schema(
        description = "Name of the host system which manages the item.",
        examples = ["AXIELL", "ALMA", "ASTA", "BIBLIOFIL"]
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
        examples = ["BOOK", "ISSUE", "Arkivmateriale", "Film_Frys"]
    )
    val itemCategory: String,
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
        description = "Who's the owner of the item.",
        examples = ["NB", "ARKIVVERKET"]
    )
    val owner: Owner,
    @Schema(
        description = "Callback URL for the item used to update the item in the host system.",
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
                "If the item is in storage then quantity is 1.0, if it's not in storage then quantity is 0.0.",
        examples = [ "0.0", "1.0"],
        accessMode = READ_ONLY,
        required = false
    )
    val quantity: Double?
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
