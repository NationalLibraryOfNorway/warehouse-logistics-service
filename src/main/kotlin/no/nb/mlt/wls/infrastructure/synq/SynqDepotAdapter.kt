package no.nb.mlt.wls.infrastructure.synq

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nb.mlt.wls.domain.model.AssociatedStorage
import no.nb.mlt.wls.domain.model.Environment
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Item
import no.nb.mlt.wls.domain.model.ItemCategory
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.StorageSystemFacade
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class SynqDepotAdapter(
    private val synqAdapter: SynqAdapter
) : StorageSystemFacade {
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
        // Depot SynQ should only get items from DEPOT, which has its own categories and no preferred env.
        // But for safety we check everything just in case, so we don't end up goofing it up

        if (item.preferredEnvironment != Environment.NONE) return false

        if (item.hostName != HostName.BIBLIOFIL_DEP && item.hostName != HostName.BIBLIOFIL_DFB) return false

        return when (item.itemCategory) {
            ItemCategory.MICROFILM, ItemCategory.MONOGRAPH, ItemCategory.PERIODICAL -> true
            else -> false
        }
    }

    override fun getName(): String = "Depot SynQ"
}
