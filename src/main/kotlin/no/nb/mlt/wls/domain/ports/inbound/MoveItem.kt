package no.nb.mlt.wls.domain.ports.inbound

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
interface MoveItem {
    suspend fun moveItem(moveItemPayload: MoveItemPayload): Item
}

data class MoveItemPayload(
    val hostId: String,
    val hostName: HostName,
    val quantity: Int,
    val location: String
) {
    fun validate() {
        if (quantity < 0.0) {
            throw ValidationException("Quantity can not be negative")
        }

        if (location.isBlank()) {
            throw ValidationException("Location can not be blank")
        }
    }
}
