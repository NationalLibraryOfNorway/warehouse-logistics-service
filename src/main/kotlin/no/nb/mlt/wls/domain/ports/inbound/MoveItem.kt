package no.nb.mlt.wls.domain.ports.inbound

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item

/**
 * A port for moving items in the system.
 *
 * An item move is different from an item update, even though they contain the same info.
 * An item move says that item moved to a new location and how many items have moved.
 * In our case this will always be one item.
 * An item update says that the item has updated and provides its new location and quantity.
 * This can be 0 or 1.
 *
 * These ports are used as storage systems can send either one of those types of messages.
 * For example, the old SynQ sends an item update, while the Autostore sends an item move message.
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
    @field:Min(value = 0, message = "Quantity on hand must not be negative. It must be zero or higher")
    val quantity: Int,
    @field:NotBlank(message = "Location can not be blank")
    val location: String
)
