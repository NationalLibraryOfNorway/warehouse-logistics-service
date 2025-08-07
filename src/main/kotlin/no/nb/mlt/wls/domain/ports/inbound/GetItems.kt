package no.nb.mlt.wls.domain.ports.inbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item

/**
 * A port for retrieving all items from specified host systems.
 * Given a list of [HostName], it retrieves the corresponding [Item] entities
 * associated with those hosts.
 *
 * This interface is typically used for scenarios where multiple host systems
 * need to be queried to retrieve their respective items. The function is
 * asynchronous and returns a list of items that belong to the specified hostnames.
 *
 * @see Item
 * @see HostName
 */
fun interface GetItems {
    suspend fun getAllItems(hostnames: List<HostName>): List<Item>
}
