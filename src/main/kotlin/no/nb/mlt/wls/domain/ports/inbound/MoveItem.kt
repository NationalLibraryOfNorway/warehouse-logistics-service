package no.nb.mlt.wls.domain.ports.inbound

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item

/**
 * A port for moving items in the system.
 *
 * An item move is different from an item update, even though they contain the same info.
 * An item update says that the item has updated and provides its new location and quantity.
 * An item move says that item moved to a new location and how many items have moved.
 * In our case this will always be one item, so the quantity can be -1, 0 or 1.
 *
 * These ports are used as storage systems can send either one of those types of messages.
 * For example, the old SynQ sends an item update, while the Autostore sends an item move message.
 * We will generate a payload with -1 if the message specifically moves from AutoStore to its picking station,
 * as this signifies the item being taken out of the system.
 *
 * @see MoveItemPayload
 * @see UpdateItem
 * @see Item
 */
fun interface MoveItem {
    suspend fun moveItem(moveItemPayload: MoveItemPayload): Item
}

data class MoveItemPayload(
    val hostName: HostName,
    @field:NotBlank(message = "Host ID is missing, and can not be blank")
    val hostId: String,
    @field:Max(value = 1, message = "Quantity on hand is too large. It must be zero, one, or negative one")
    @field:Min(value = -1, message = "Quantity on hand is too small. It must be zero, one, or negative one")
    val quantity: Int,
    @field:NotBlank(message = "Location can not be blank")
    val location: String,
    @field:NotNull(message = "Associated storage system can not be blank")
    val associatedStorage: AssociatedStorage
)
