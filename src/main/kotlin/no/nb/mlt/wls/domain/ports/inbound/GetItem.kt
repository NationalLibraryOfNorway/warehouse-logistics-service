package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item

/**
 * A port for retrieving a specific item from a host system.
 * The function returns the corresponding [Item] if found, or `null` if no matching item exists.
 *
 * @see Item
 */
fun interface GetItem {
    suspend fun getItem(
        hostName: HostName,
        hostId: String
    ): Item?
}
