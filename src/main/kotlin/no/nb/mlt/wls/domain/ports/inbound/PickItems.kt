package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item

/**
 * A port for handling the process of picking items from a storage system.
 *
 * This interface is designed to perform operations where specific items for a given host
 * system are picked.
 *
 * Implementations of this interface are responsible for executing the logic associated
 * with the picking operation, which might include inventory adjustments, storage updates,
 * or notifying other services about the picked items.
 *
 * @see Item
 */
fun interface PickItems {
    suspend fun pickItems(
        hostName: HostName,
        pickedItems: Map<String, Int>
    )
}
