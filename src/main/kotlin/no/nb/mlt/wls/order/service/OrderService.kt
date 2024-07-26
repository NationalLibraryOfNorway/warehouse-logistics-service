package no.nb.mlt.wls.order.service

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
    fun createOrder(payload: ApiOrderPayload): ResponseEntity<ApiOrderPayload> {
        // TODO - Order validation?

        val existingOrder = db.getByHostNameAndHostOrderId(payload.hostName, payload.orderId)
        if (existingOrder != null) {
            return ResponseEntity.ok(existingOrder.toApiOrderPayload())
        }

        val synqResponse = synqService.createOrder(payload.toOrder().toSynqPayload())
        if (!synqResponse.statusCode.is2xxSuccessful) {
            return ResponseEntity.internalServerError().build()
        }

        try {
            db.save(payload.toOrder())
        } catch (e: Exception) {
            throw ServerErrorException("Failed to save product in database, but created in storage system", e)
        }

        return ResponseEntity.status(HttpStatus.CREATED).build()
    }
}
