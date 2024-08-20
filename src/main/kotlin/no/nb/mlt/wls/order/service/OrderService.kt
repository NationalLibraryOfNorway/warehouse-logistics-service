package no.nb.mlt.wls.order.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import no.nb.mlt.wls.order.payloads.ApiOrderPayload
import no.nb.mlt.wls.order.payloads.toApiOrderPayload
import no.nb.mlt.wls.order.payloads.toOrder
import no.nb.mlt.wls.order.payloads.toSynqPayload
import no.nb.mlt.wls.order.repository.OrderRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.server.ServerErrorException
import org.springframework.web.server.ServerWebInputException
import java.time.Duration
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Service
class OrderService(val db: OrderRepository, val synqService: SynqOrderService) {
    suspend fun createOrder(payload: ApiOrderPayload): ResponseEntity<ApiOrderPayload> {
        throwIfInvalidPayload(payload)

        val existingOrder =
            db.findByHostNameAndHostOrderId(payload.hostName, payload.orderId)
                .timeout(Duration.ofSeconds(8))
                .onErrorMap(TimeoutException::class.java) {
                    logger.error(it) {
                        "Timed out while fetching from WLS database. Relevant payload: $payload"
                    }
                    ServerErrorException("Failed to create the order in storage system", it)
                }
                .awaitSingleOrNull()

        if (existingOrder != null) {
            return ResponseEntity.badRequest().build()
        }

        val synqResponse = synqService.createOrder(payload.toOrder().toSynqPayload())
        // If SynQ didn't throw an error, but returned something that wasn't a new order,
        // then it is likely some error or edge-case
        if (!synqResponse.statusCode.isSameCodeAs(HttpStatus.CREATED)) {
            throw ServerErrorException("Unexpected error with SynQ", null)
        }

        // Return what the database saved, as it could contain changes
        val order =
            db.save(payload.toOrder())
                .timeout(Duration.ofSeconds(6))
                .onErrorMap(TimeoutException::class.java) {
                    logger.error { "Saving order timed out for payload: %s".format(payload.toString()) }
                    ServerErrorException("Failed to create the order in storage system", it)
                }
                .awaitSingle()

        return ResponseEntity.status(HttpStatus.CREATED).body(order.toApiOrderPayload())
    }

    private fun throwIfInvalidPayload(payload: ApiOrderPayload) {
        if (payload.orderId.isBlank()) {
            throw ServerWebInputException("The order's orderId is required, and can not be blank")
        }
        if (payload.hostOrderId.isBlank()) {
            throw ServerWebInputException("The order's hostOrderId is required, and can not be blank")
        }
        if (payload.productLine.isEmpty()) {
            throw ServerWebInputException("The order does not contain any product lines, and is therefore invalid")
        }
    }
}
