package no.nb.mlt.wls.order.service

import kotlinx.coroutines.reactive.awaitFirstOrNull
import no.nb.mlt.wls.order.payloads.ApiOrderPayload
import no.nb.mlt.wls.order.payloads.toApiOrderPayload
import no.nb.mlt.wls.order.payloads.toOrder
import no.nb.mlt.wls.order.payloads.toSynqPayload
import no.nb.mlt.wls.order.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.web.server.ServerErrorException
import reactor.core.publisher.Mono

@Service
class OrderService(val db: OrderRepository, val synqService: SynqOrderService) {
    suspend fun createOrder(payload: ApiOrderPayload): Mono<ApiOrderPayload> {
        val existingOrder =
            db.getByHostNameAndHostOrderId(payload.hostName, payload.orderId)
                .awaitFirstOrNull()

        if (existingOrder != null) {
            return Mono.just(existingOrder.toApiOrderPayload())
        }

        return synqService.createOrder(payload.toOrder().toSynqPayload())
            .doOnError {
                throw ServerErrorException("Failed to create order in storage system", it)
            }
            .doOnSuccess {
                try {
                    db.save(payload.toOrder())
                } catch (e: Exception) {
                    throw ServerErrorException("Failed to save product in database, but created in storage system", e)
                }
            }
            .then(Mono.just(payload))
    }
}
