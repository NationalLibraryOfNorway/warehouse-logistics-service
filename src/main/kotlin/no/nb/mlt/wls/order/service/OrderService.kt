package no.nb.mlt.wls.order.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.throwIfInvalidClientName
import no.nb.mlt.wls.order.model.Order
import no.nb.mlt.wls.order.model.OrderStatus
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
    /**
     * Creates an order within the WLS database, and sends it to the appropriate storage systems
     */
    suspend fun createOrder(
        clientName: String,
        payload: ApiOrderPayload
    ): ResponseEntity<ApiOrderPayload> {
        throwIfInvalidClientName(clientName, payload.hostName)
        throwIfInvalidPayload(payload)
        val existingOrder = findOrderInDb(payload.hostName, payload.hostOrderId)

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

    suspend fun updateOrder(
        payload: ApiOrderPayload,
        clientName: String
    ): ResponseEntity<ApiOrderPayload> {
        throwIfInvalidClientName(clientName, payload.hostName)
        throwIfInvalidPayload(payload)

        val existingOrder =
            findOrderInDb(payload.hostName, payload.hostOrderId)
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Order with id $payload.hostOrderId from $payload.hostName does not exist in the database"
                )

        if (existingOrder.status != OrderStatus.NOT_STARTED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Order is already being processed, and can not be edited")
        }
        synqService.updateOrder(payload)

        // Saving here will override the existing order, as the id's match
        val updatedOrder =
            db.save(
                existingOrder.copy(
                    hostOrderId = payload.hostOrderId,
                    hostName = payload.hostName,
                    productLine = payload.productLine,
                    orderType = payload.orderType,
                    owner = payload.owner,
                    receiver = payload.receiver,
                    callbackUrl = payload.callbackUrl
                )
            ).awaitSingle()

        return ResponseEntity.ok(updatedOrder.toApiOrderPayload())
    }

    /**
     * Gets an order from the WLS database
     */
    suspend fun getOrder(
        clientName: String,
        hostName: HostName,
        hostOrderId: String
    ): ResponseEntity<Order> {
        throwIfInvalidClientName(clientName, hostName)
        val order =
            findOrderInDb(hostName, hostOrderId) ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Order with id $hostOrderId from $hostName was not found"
            )
        return ResponseEntity.ok(order)
    }

    /**
     * Query the WLS database if an order exists
     */
    private suspend fun findOrderInDb(
        hostName: HostName,
        hostOrderId: String
    ): Order? {
        return db.findByHostNameAndHostOrderId(hostName, hostOrderId)
            .timeout(Duration.ofSeconds(8))
            .onErrorMap {
                if (it is TimeoutException) {
                    logger.error(it) {
                        "Timed out while fetching order $hostOrderId from WLS database. Owner: $hostName"
                    }
                } else {
                    logger.error(it) { "Unexpected error for getting $hostOrderId from $hostName" }
                }
                ServerErrorException("Failed while checking if order already exists in the database", it)
            }
            .awaitSingleOrNull()
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
