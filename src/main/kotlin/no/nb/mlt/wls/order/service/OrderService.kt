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
import org.springframework.web.server.ResponseStatusException
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
                .onErrorMap {
                    if (it is TimeoutException) {
                        logger.error(it) {
                            "Timed out while fetching from WLS database. Relevant payload: $payload"
                        }
                    } else {
                        logger.error(it) { "Unexpected error for $payload" }
                    }
                    ServerErrorException("Failed while checking if order already exists in the database", it)
                }
                .awaitSingleOrNull()

        if (existingOrder != null) {
            return ResponseEntity.ok(existingOrder.toApiOrderPayload())
        }

        val synqResponse = synqService.createOrder(payload.toOrder().toSynqPayload())
        // If SynQ returned a 200 OK then it means it exists from before, and we can return empty response (since we don't have any order info)
        if (synqResponse.statusCode.isSameCodeAs(HttpStatus.OK)) {
            return ResponseEntity.ok().build()
        }
        // If SynQ returned anything else than 200 or 201 it's an error
        if (!synqResponse.statusCode.isSameCodeAs(HttpStatus.CREATED)) {
            throw ServerErrorException("Unexpected error with SynQ", null)
        }

        // Return what the database saved, as it could contain changes
        val order =
            db.save(payload.toOrder())
                .timeout(Duration.ofSeconds(6))
                .onErrorMap(TimeoutException::class.java) {
                    logger.error { "Saving order timed out for payload: %s".format(payload.toString()) }
                    ServerErrorException("Failed to save the order in the database", it)
                }
                .awaitSingle()

        return ResponseEntity.status(HttpStatus.CREATED).body(order.toApiOrderPayload())
    }

    suspend fun updateOrder(payload: ApiOrderPayload): ResponseEntity<ApiOrderPayload> {
        throwIfInvalidPayload(payload)

        val existingOrder =
            db.findByHostNameAndHostOrderId(payload.hostName, payload.orderId)
                .timeout(Duration.ofSeconds(8))
                .onErrorMap {
                    if (it is TimeoutException) {
                        logger.error(it) {
                            "Timed out while fetching from WLS database. Relevant payload: $payload"
                        }
                    } else {
                        logger.error(it) { "Unexpected error for $payload" }
                    }
                    ServerErrorException("Failed while checking if order already exists in the database", it)
                }
                .awaitSingleOrNull()

        if (existingOrder == null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Order with id $payload.hostOrderId from $payload.hostName does not exist in the database"
            )
        }

        synqService.updateOrder(payload)

        // Saving here will override the existing order, as the id's match
        val updatedOrder =
            db.save(payload.toOrder())
                .awaitSingle()

        return ResponseEntity.ok(updatedOrder.toApiOrderPayload())
    }

    private fun throwIfInvalidPayload(payload: ApiOrderPayload) {
        if (payload.orderId.isBlank()) {
            throw ServerWebInputException("The order's orderId is required, and can not be blank")
        }
        if (payload.hostOrderId.isBlank()) {
            throw ServerWebInputException("The order's hostOrderId is required, and can not be blank")
        }
        if (payload.productLine.isEmpty()) {
            throw ServerWebInputException("The order must contain product lines")
        }
    }
}
