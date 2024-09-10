package no.nb.mlt.wls.infrastructure.repositories.order

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.outbound.OrderRepository

import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Component
class MongoOrderRepositoryAdapter(
    private val orderMongoRepository: OrderMongoRepository
) : OrderRepository {
    override fun getOrder(hostName: HostName, hostOrderId: String): Mono<Order> {
        return orderMongoRepository.findByHostNameAndHostOrderId(hostName, hostOrderId)
    }

    override fun deleteOrder(hostName: HostName, hostOrderId: String): Mono<Void> {
        return orderMongoRepository.deleteByHostNameAndHostOrderId(hostName, hostOrderId)
    }

    override fun updateOrder(order: Order): Mono<Order> {
        TODO("Not yet implemented")
    }

    override fun createOrder(order: Order): Mono<Order> {
        TODO("Not yet implemented")
    }
}

@Repository
interface OrderMongoRepository : ReactiveMongoRepository<Order, String> {
    fun findByHostNameAndHostOrderId(
        hostName: HostName,
        hostOrderId: String
    ): Mono<Order>

    fun deleteByHostNameAndHostOrderId(
        hostName: HostName,
        hostOrderId: String
    ): Mono<Void>
}
