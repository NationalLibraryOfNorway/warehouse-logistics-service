package no.nb.mlt.wls.product.controller

import no.nb.mlt.wls.product.payloads.SynqProductPayload
import no.nb.mlt.wls.product.service.SynqService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/synq")
class SynqController(val synqService: SynqService) {
    @PostMapping("/resources/nbproducts/")
    fun createProduct(
        authentication: Authentication,
        @RequestBody payload: SynqProductPayload
    ) {
        authentication.authorities.map { println(it.authority) }
        synqService.test(payload)
    }
}
