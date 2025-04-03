package no.nb.mlt.wls.domain.ports.inbound

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item

/**
 * This port is used for handling messages regarding items moving
 * inside the storage system.
 * Some examples include handling status messages when crates arrive
 * at picking stations, or when items return to the
 * storage systems.
 * In both cases we want to know where the item went, and if the count changed
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
