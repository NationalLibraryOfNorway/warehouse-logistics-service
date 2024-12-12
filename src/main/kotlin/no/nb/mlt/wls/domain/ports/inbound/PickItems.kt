package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName

/**
 * This interface handles updating item counts when an item is picked out of
 * a storage system.
 */
fun interface PickItems {
    suspend fun pickItems(
        hostName: HostName,
        itemsPickedMap: Map<String, Int>
    )
}
