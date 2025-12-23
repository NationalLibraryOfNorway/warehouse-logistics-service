package no.nb.mlt.wls.infrastructure.repositories.order

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import no.nb.mlt.wls.domain.ports.inbound.exceptions.OrderNotFoundException
import no.nb.mlt.wls.domain.ports.outbound.OrderRepository
import no.nb.mlt.wls.domain.ports.outbound.RepositoryException
import no.nb.mlt.wls.domain.ports.outbound.exceptions.OrderUpdateException
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
            .map(MongoOrder::toOrder)
            .timeout(timeoutConfig.mongo)
            .doOnError {
                logger.error(it) {
                    if (it is TimeoutException) {
                        "Timed out while fetching from WLS database. Order ID: $hostOrderId, Host: $hostName"
                    } else {
                        "Error while fetching order"
                    }
                }
            }.onErrorMap {
                RepositoryException("Unable to fetch the order")
            }.awaitSingleOrNull()

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

    override suspend fun deleteOrder(order: Order): Boolean {
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
                }.awaitSingle()

        return modified != 0L
    }

    override suspend fun updateOrder(order: Order): Boolean {
        val ordersModified =
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
                .awaitSingle()

        when (ordersModified) {
            0L -> {
                logger.warn { "Order was not updated. $order" }
                return false
            }
            1L -> {
                logger.debug { "Order ${order.hostOrderId} for ${order.hostName} was updated." }
                return true
            }
            else -> throw RepositoryException(
                "MongoOrderRepository modified too many orders. Modified $ordersModified orders with ID: ${order.hostOrderId}, and hostname: ${order.hostName}"
            )
        }
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
            .findOrdersByHostNameAndHostIds(hostName, orderItemIds)
            .map { it.toOrder() }
            .timeout(timeoutConfig.mongo)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while bulk fetching orders in WLS database. HostName: $hostName, Items: $orderItemIds"
                }
            }.collectList()
            .awaitSingle()

    override suspend fun getOrdersWithPickedItems(
        hostName: HostName,
        orderItemIds: List<String>
    ): List<Order> =
        orderMongoRepository
            .findPickedOrdersByHostNameAndHostIds(hostName, orderItemIds)
            .map { it.toOrder() }
            .timeout(timeoutConfig.mongo)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while bulk fetching orders in WLS database. HostName: $hostName, Items: $orderItemIds"
                }
            }.collectList()
            .awaitSingle()

    override suspend fun getAllOrdersWithHostId(
        hostNames: List<HostName>,
        hostOrderId: String
    ): List<Order> =
        orderMongoRepository
            .findAllByHostNameInAndHostOrderIdIgnoreCase(hostNames, hostOrderId)
            .map { it.toOrder() }
            .timeout(timeoutConfig.mongo)
            .doOnError(TimeoutException::class.java) {
                logger.error(it) {
                    "Timed out while bulk fetching orders in WLS database. HostNames: $hostNames, Order ID: $hostOrderId"
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
    @Update($$"{'$set':{status: ?2,orderLine: ?3,orderType: ?4,contactPerson: ?5,address: ?6, callbackUrl: ?7}}")
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

    @Query($$"{hostName: ?0, \"orderLine.hostId\": {$in: ?1}}")
    fun findOrdersByHostNameAndHostIds(
        hostName: HostName,
        orderLine: List<String>
    ): Flux<MongoOrder>

    @Query($$"""{hostName: ?0, orderLine: {$elemMatch: {hostId: {$in: ?1}, status: "PICKED"}}}""")
    fun findPickedOrdersByHostNameAndHostIds(
        hostName: HostName,
        orderLine: List<String>
    ): Flux<MongoOrder>

    fun findAllByHostNameIn(hostnames: Collection<HostName>): Flux<MongoOrder>

    fun findAllByHostNameInAndHostOrderIdIgnoreCase(
        hostnames: Collection<HostName>,
        hostOrderId: String
    ): Flux<MongoOrder>
}
