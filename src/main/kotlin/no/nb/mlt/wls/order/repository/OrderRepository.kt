package no.nb.mlt.wls.order.repository

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.order.model.Order
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderRepository : MongoRepository<Order, String> {
    fun getByHostNameAndHostOrderId(
        hostName: HostName,
        hostOrderId: String
    ): Order?
}
