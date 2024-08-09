package no.nb.mlt.wls.order.service

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import no.nb.mlt.wls.order.model.Order
import no.nb.mlt.wls.order.payloads.ApiOrderPayload
import no.nb.mlt.wls.order.payloads.toApiOrderPayload
import no.nb.mlt.wls.order.payloads.toOrder
import no.nb.mlt.wls.order.payloads.toSynqPayload
import no.nb.mlt.wls.order.repository.OrderRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.server.ServerErrorException

@Service
class OrderService(val db: OrderRepository, val synqService: SynqOrderService) {
    suspend fun createOrder(payload: ApiOrderPayload): ResponseEntity<ApiOrderPayload> {
        val existingOrder = getByHostNameAndHostOrderId(payload)

        if (existingOrder != null) {
            return ResponseEntity.badRequest().build()
        }

        try {
            // TODO - Handle?
            val synqResponse = synqService.createOrder(payload.toOrder().toSynqPayload())
            // Return what the database saved, as it could contain changes
            val order = db.save(payload.toOrder()).awaitSingle()
            return ResponseEntity.status(HttpStatus.CREATED).body(order.toApiOrderPayload())
        } catch (e: Exception) {
            throw ServerErrorException("Failed to create order in storage system", e)
        }
    }

    suspend fun getByHostNameAndHostOrderId(payload: ApiOrderPayload): Order? {
        return db.getByHostNameAndHostOrderId(payload.hostName, payload.orderId).awaitSingleOrNull()
    }
}
