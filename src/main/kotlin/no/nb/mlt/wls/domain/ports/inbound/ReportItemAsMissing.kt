package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.MISSING
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.exceptions.ItemNotFoundException

/**
 * A port for marking items as missing.
 *
 * This interface is designed to handle cases where a storage handler reports an item as missing.
 * The implementation should update the item identified by its unique host name and host ID,
 * and then update its location to [MISSING].
 *
 * This should also update any related orders which contain the item, so that they can be completed.
 *
 * This operation may throw an [ItemNotFoundException] if the item with the specified host ID cannot be found.
 * @see Item
 * @see Order.markMissing
 * @see ItemNotFoundException
 */
fun interface ReportItemAsMissing {
    @Throws(ItemNotFoundException::class)
    suspend fun reportItemMissing(
        hostName: HostName,
        hostId: String
    ): Item
}
