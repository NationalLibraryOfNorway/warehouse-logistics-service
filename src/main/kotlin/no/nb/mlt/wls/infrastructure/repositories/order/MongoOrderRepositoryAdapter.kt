package no.nb.mlt.wls.infrastructure.repositories.order

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.outbound.OrderRepository
import no.nb.mlt.wls.domain.ports.outbound.OrderUpdateException
import no.nb.mlt.wls.infrastructure.config.TimeoutProperties
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.data.mongodb.repository.Update
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
class MongoOrderRepositoryAdapter(
    private val orderMongoRepository: OrderMongoRepository,
    private val timeoutConfig: TimeoutProperties
) : OrderRepository {
    override suspend fun getOrder(
        hostName: HostName,
        hostOrderId: String
    ): Order? =
        orderMongoRepository
            .findByHostNameAndHostOrderId(hostName, hostOrderId)
            .timeout(timeoutConfig.mongo)
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while fetching from WLS database. Order ID: $hostOrderId, Host: $hostName"
                    } else {
                        "Error while fetching order"
                    }
                }
            }.onErrorMap { OrderNotFoundException(it.message ?: "Could not fetch order") }
            .awaitSingleOrNull()
            ?.toOrder()

    override suspend fun getAllOrdersForHosts(hostnames: List<HostName>): List<Order> =
        orderMongoRepository
            .findAllByHostNameIn(hostnames)
            .collectList()
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while fetching all orders for $hostnames"
                }
            }.awaitSingle()
            .map { it.toOrder() }

    override suspend fun deleteOrder(order: Order) {
        val modified =
            orderMongoRepository
                .findAndUpdateByHostNameAndHostOrderId(
                    hostName = order.hostName,
                    hostOrderId = order.hostOrderId,
                    status = Order.Status.DELETED,
                    orderLine = order.orderLine,
                    orderType = order.orderType,
                    contactPerson = order.contactPerson,
                    address = order.address,
                    callbackUrl = order.callbackUrl
                ).timeout(timeoutConfig.mongo)
                .doOnError(TimeoutException::class.java) {
                    logger.error(it) {
                        "Timed out while deleting order from WLS database. Order ID: ${order.hostOrderId}, Host: ${order.hostName}"
                    }
                }.awaitSingleOrNull()

        if (modified == 0L) throw OrderNotFoundException("Order ${order.hostOrderId} for ${order.hostName} was not found for deletion")
    }

    override suspend fun updateOrder(order: Order): Order {
        orderMongoRepository
            .findAndUpdateByHostNameAndHostOrderId(
                order.hostName,
                order.hostOrderId,
                order.status,
                order.orderLine,
                order.orderType,
                order.contactPerson,
                order.address,
                order.callbackUrl
            ).timeout(timeoutConfig.mongo)
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while updating order. Order ID: ${order.hostOrderId}, Host: ${order.hostName}"
                    } else {
                        "Error while updating order"
                    }
                }
            }.onErrorMap { OrderUpdateException(it.message ?: "Could not update order", it) }
            .awaitSingleOrNull()

        return getOrder(order.hostName, order.hostOrderId)!!
    }

    override suspend fun createOrder(order: Order): Order =
        orderMongoRepository
            .save(order.toMongoOrder())
            .map { it.toOrder() }
            .timeout(timeoutConfig.mongo)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while updating order in WLS database. Order: $order"
                }
            }.awaitSingle()

    override suspend fun getOrdersWithItems(
        hostName: HostName,
        orderItemIds: List<String>
    ): List<Order> =
        orderMongoRepository
            .findAllOrdersWithHostNameAndOrderItems(hostName, orderItemIds)
            .map { it.toOrder() }
            .timeout(timeoutConfig.mongo)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while bulk fetching orders in WLS database. HostName: $hostName, Items: $orderItemIds"
                }
            }.collectList()
            .awaitSingle()
}

@Repository
interface OrderMongoRepository : ReactiveMongoRepository<MongoOrder, String> {
    fun findByHostNameAndHostOrderId(
        hostName: HostName,
        hostOrderId: String
    ): Mono<MongoOrder>

    @Query("{hostName: ?0, hostOrderId: ?1}")
    @Update("{'\$set':{status: ?2,orderLine: ?3,orderType: ?4,contactPerson: ?5,address: ?6, callbackUrl: ?7}}")
    fun findAndUpdateByHostNameAndHostOrderId(
        hostName: HostName,
        hostOrderId: String,
        status: Order.Status,
        orderLine: List<Order.OrderItem>,
        orderType: Order.Type,
        contactPerson: String,
        address: Order.Address?,
        callbackUrl: String
    ): Mono<Long>

    @Query("{hostName: ?0, \"orderLine.hostId\": {\$in: ?1}, \"orderLine.status\": \"PICKED\"}")
    fun findAllOrdersWithHostNameAndOrderItems(
        hostName: HostName,
        orderLine: List<String>
    ): Flux<MongoOrder>

    fun findAllByHostNameIn(hostnames: Collection<HostName>): Flux<MongoOrder>
}
