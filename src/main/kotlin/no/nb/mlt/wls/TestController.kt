package no.nb.mlt.wls

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.product.payloads.ApiProductPayload
import no.nb.mlt.wls.product.payloads.toPayload
import no.nb.mlt.wls.product.payloads.toProduct
import no.nb.mlt.wls.product.service.ProductService
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/test")
class TestController(val productService: ProductService) {
    @GetMapping("/open", produces = [APPLICATION_JSON_VALUE])
    fun open(): ResponseEntity<Response> {
        return ResponseEntity.ok(Response("Hello to an open endpoint!"))
    }

    @GetMapping("/authenticated", produces = [APPLICATION_JSON_VALUE])
    fun authenticated(authentication: Authentication): ResponseEntity<Response> {
        return ResponseEntity.ok(Response("Hello ${authentication.name} to an authenticated endpoint!"))
    }

    @GetMapping("/authorized", produces = [APPLICATION_JSON_VALUE])
    fun authorized(authentication: Authentication): ResponseEntity<Response> {
        return ResponseEntity.ok(
            Response(
                """
                Hello ${authentication.name} to an endpoint that only users with the 'wls-role' authority can access!
                You have following authorities: ${authentication.authorities.joinToString(", ")}
                """.trimIndent()
            )
        )
    }

    @GetMapping("/json", produces = [APPLICATION_JSON_VALUE])
    fun getJson(
        authentication: Authentication,
        @RequestParam hostName: HostName
    ): ResponseEntity<List<ApiProductPayload>> {
        val list = productService.getByHostName(hostName).map { it.toPayload() }
        return ResponseEntity.ok(list)
    }

    @PostMapping("/json", produces = [APPLICATION_JSON_VALUE])
    fun produceJson(
        authentication: Authentication,
        @RequestBody payload: ApiProductPayload
    ): ResponseEntity<ApiProductPayload> {
        try {
            productService.save(payload.toProduct())
            return ResponseEntity.ok(payload)
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().build()
        }
    }

    class Response(val message: String)
}
