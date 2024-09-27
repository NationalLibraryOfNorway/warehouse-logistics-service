package no.nb.mlt.wls.application.restapi

import no.nb.mlt.wls.domain.ports.inbound.UpdateOrder
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// TODO - Repackage
@RestController
@RequestMapping("/synq/v1")
class SynqMessageController(
//    private val updateItem: UpdateItem,
    private val updateOrder: UpdateOrder
) {
    @PutMapping("/order-update/{owner}/{orderId}")
    fun handleStatusUpdate(
        @PathVariable owner: String,
        @PathVariable orderId: String,
        @RequestBody payload: OrderStatusUpdate,
        @AuthenticationPrincipal caller: JwtAuthenticationToken
    ) {
        println("Hello world! Order update received")
        println(payload)
        TODO("update the order")
    }

    @PutMapping("/pick-update/{owner}/{orderId}")
    fun handleOrderPicking(
        @PathVariable owner: String,
        @PathVariable orderId: String,
        @RequestBody payload: OrderPickedConfirmation,
        @AuthenticationPrincipal caller: JwtAuthenticationToken
    ) {
        println("Heya world! Someone picked my nose :/")
        println()
        TODO("update the order")
    }

    @PutMapping("/product-update")
    fun handleItemUpdate(
        @RequestBody payload: ItemUpdate,
        @AuthenticationPrincipal caller: JwtAuthenticationToken
    ) {
        TODO("Update the product")
    }
}
