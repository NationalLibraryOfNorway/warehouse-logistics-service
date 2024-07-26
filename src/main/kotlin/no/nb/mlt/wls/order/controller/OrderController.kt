package no.nb.mlt.wls.order.controller

import no.nb.mlt.wls.order.payloads.ApiOrderPayload
import no.nb.mlt.wls.order.service.OrderService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["", "/v1"])
class OrderController(val orderService: OrderService) {
    // TODO - Swagger
    @PostMapping("/order/batch/create")
    fun createOrder(
        @RequestBody payload: ApiOrderPayload
    ): ResponseEntity<ApiOrderPayload> = orderService.createOrder(payload)
}
