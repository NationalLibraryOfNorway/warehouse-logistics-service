package no.nb.mlt.wls.order.service

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import no.nb.mlt.wls.order.payloads.ApiOrderPayload
import no.nb.mlt.wls.order.payloads.toApiOrderPayload
import no.nb.mlt.wls.order.payloads.toOrder
import no.nb.mlt.wls.order.payloads.toSynqPayload
import no.nb.mlt.wls.order.repository.OrderRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.server.ServerErrorException
import reactor.core.publisher.Mono
import java.time.Duration

@Service
class OrderService(val db: OrderRepository, val synqService: SynqOrderService) {
    suspend fun createOrder(payload: ApiOrderPayload): ResponseEntity<ApiOrderPayload> {
        val existingOrder =
            Mono.just(payload)
                .flatMap {
                    db.getByHostNameAndHostOrderId(it.hostName, it.orderId)
                }
                .awaitFirstOrNull()

        if (existingOrder != null) {
            return ResponseEntity.badRequest().build()
        }

        val newOrder =
            Mono.just(payload)
                .map {
                    synqService.createOrder(payload.toOrder().toSynqPayload())
                }
                .awaitSingle()
                .timeout(Duration.ofSeconds(6))
                .doOnError {
                    throw ServerErrorException("Failed to create order in storage system", it)
                }
                .then(Mono.just(payload.toOrder()))
                .flatMap {
                    db.save(it)
                }
                .awaitSingle()
        return ResponseEntity.status(HttpStatus.CREATED).body(newOrder.toApiOrderPayload())
    }
}
