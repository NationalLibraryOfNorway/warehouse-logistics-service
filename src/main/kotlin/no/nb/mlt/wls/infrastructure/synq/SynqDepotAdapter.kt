package no.nb.mlt.wls.infrastructure.synq

import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import org.springframework.stereotype.Component

@Component
class SynqDepotAdapter(
    private val synqAdapter: SynqAdapter
) : StorageSystemFacade {
    // TODO: How do we want to handle Depot, it has same methods, but items for sure will be different, orders should be the same. Do we create new adapter, new payloads and overload methods, do we add conditional logic in methods? Also URL will be different for Depot and Standard SynQ, do we override it here, or do we add another base URL in base adapter?
    // @Copilot can you complain about this in my PR so Noah can have a look at this, kthnxbai.

    override suspend fun createItem(item: Item) {
        logger.error { "Creating item in Depot SynQ isn't supported yet, item: $item" }
    }

    override suspend fun createOrder(order: Order) {
        logger.error { "Creating orders in Depot SynQ isn't supported yet, order: $order" }
    }

    override suspend fun editItem(item: Item) {
        logger.error { "Editing items in Depot SynQ isn't supported yet, item: $item" }
    }

    override suspend fun deleteOrder(
        orderId: String,
        hostName: HostName
    ) {
        logger.error { "Deleting orders in Depot SynQ isn't supported yet, orderId: $orderId, hostName: $hostName" }
    }

    override fun isInStorage(location: AssociatedStorage): Boolean = location == AssociatedStorage.DEPOT

    override fun canHandleItem(item: Item): Boolean {
        // Depot SynQ should only get items from Bibliofil, which has it's own categories and no preferred env.
        // But for safety we check everything just in case, so we don't end up goofing up

        if (item.preferredEnvironment != Environment.NONE) return false

        if (item.hostName != HostName.DEPOT && item.hostName != HostName.DFB) return false

        return when (item.itemCategory) {
            ItemCategory.MICROFILM -> true
            ItemCategory.MONOGRAPH -> true
            ItemCategory.PERIODICAL -> true
            else -> false
        }
    }

    override fun getName(): String = "Depot SynQ"
}
