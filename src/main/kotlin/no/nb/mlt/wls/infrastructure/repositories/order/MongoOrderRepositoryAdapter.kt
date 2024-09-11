package no.nb.mlt.wls.infrastructure.repositories.order

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.outbound.OrderRepository
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
class MongoOrderRepositoryAdapter(
    private val orderMongoRepository: OrderMongoRepository
) : OrderRepository {
    override suspend fun getOrder(hostName: HostName, hostOrderId: String): Order? {
        return orderMongoRepository.findByHostNameAndHostOrderId(hostName, hostOrderId)
            // TODO - See if timeouts can be made configurable
            .timeout(Duration.ofSeconds(8))
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while fetching from WLS database. Order ID: $hostOrderId, Host: $hostName"
                    } else {
                        "Error while fetching order"
                    }
                }
            }
            .onErrorMap { OrderNotFoundException(it.message ?: "Could not fetch order") }
            .map { it.toOrder() }
            .awaitSingleOrNull()
    }

    override suspend fun deleteOrder(hostName: HostName, hostOrderId: String) {
        getOrder(hostName, hostOrderId) ?: throw OrderNotFoundException("Could not find order for $hostName with hostId $hostOrderId")

        orderMongoRepository.deleteByHostNameAndHostOrderId(hostName, hostOrderId)
            // TODO - See if timeouts can be made configurable
            .timeout(Duration.ofSeconds(8))
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while deleting order from WLS database. Order ID: $hostOrderId, Host: $hostName"
                }
            }
            .awaitSingleOrNull()
    }

    override suspend fun updateOrder(order: Order): Order {
        TODO("Not yet implemented")
    }

    override suspend fun createOrder(order: Order): Order {
        return orderMongoRepository.save(order.toMongoOrder())
            .map { it.toOrder() }
            // TODO - See if timeouts can be made configurable
            .timeout(Duration.ofSeconds(8))
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while updating order in WLS database. Order: $order"
                }
            }
            .awaitSingle()
    }
}

@Repository
interface OrderMongoRepository : ReactiveMongoRepository<MongoOrder, String> {
    fun findByHostNameAndHostOrderId(
        hostName: HostName,
        hostOrderId: String
    ): Mono<MongoOrder>

    fun deleteByHostNameAndHostOrderId(
        hostName: HostName,
        hostOrderId: String
    ): Mono<Void>
}
