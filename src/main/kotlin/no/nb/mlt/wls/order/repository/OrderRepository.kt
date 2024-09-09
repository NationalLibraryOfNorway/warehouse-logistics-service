package no.nb.mlt.wls.order.repository

import no.nb.mlt.wls.domain.HostName
import no.nb.mlt.wls.order.model.Order
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface OrderRepository : ReactiveMongoRepository<Order, String> {
    fun findByHostNameAndHostOrderId(
        hostName: HostName,
        hostOrderId: String
    ): Mono<Order>

    fun deleteByHostNameAndHostOrderId(
        hostName: HostName,
        hostOrderId: String
    ): Mono<Void>
}
