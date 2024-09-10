package no.nb.mlt.wls.domain.ports.outbound

import no.nb.mlt.wls.domain.model.HostName
import no.nb.mlt.wls.domain.model.Order
import reactor.core.publisher.Mono


interface OrderRepository {
    fun getOrder(hostName: HostName, hostOrderId: String): Mono<Order>
    fun deleteOrder(hostName: HostName, hostOrderId: String): Mono<Void>
    fun updateOrder(order:Order): Mono<Order>
    fun createOrder(order:Order): Mono<Order>
}
