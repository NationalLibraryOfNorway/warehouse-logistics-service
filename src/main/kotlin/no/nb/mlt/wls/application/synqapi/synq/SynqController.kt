package no.nb.mlt.wls.application.synqapi.synq

import io.swagger.v3.oas.annotations.tags.Tag
import no.nb.mlt.wls.application.hostapi.order.ApiOrderPayload
import no.nb.mlt.wls.application.hostapi.order.throwIfInvalid
import no.nb.mlt.wls.application.hostapi.order.toApiOrderPayload
import no.nb.mlt.wls.domain.model.throwIfInvalidClientName
import no.nb.mlt.wls.domain.ports.inbound.CreateOrderDTO
import no.nb.mlt.wls.domain.ports.inbound.OrderStatusUpdate
import no.nb.mlt.wls.domain.ports.inbound.UpdateOrder
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = [ "/synq/v1"])
@Tag(name = "SynQ Controller", description = "API for receiving product and order updates from SynQ in Hermes WLS")
class SynqController(
    private val orderStatusUpdate: OrderStatusUpdate
) {

    @PostMapping("/order-update")
    suspend fun updateOrder(
        @AuthenticationPrincipal jwt: JwtAuthenticationToken,
        @RequestBody orderUpdatePayload: SynqOrderStatusUpdatePayload
    ): ResponseEntity<String> {
        return ResponseEntity
            .status(HttpStatus.OK)
            .body("OK")
    }

}
