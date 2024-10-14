package no.nb.mlt.wls.infrastructure.callbacks

import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.Owner
import no.nb.mlt.wls.domain.model.Packaging

data class NotificationItemPayload(
    val hostId: String,
    val hostName: HostName,
    val description: String,
    val itemCategory: String,
    val preferredEnvironment: Environment,
    val packaging: Packaging,
    val owner: Owner,
    val callbackUrl: String?,
    val location: String?,
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
