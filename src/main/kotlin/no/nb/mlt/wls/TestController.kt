package no.nb.mlt.wls

import jakarta.validation.Valid
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Products
import no.nb.mlt.wls.core.service.ProductsService
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/test")
class TestController(val productsService: ProductsService) {
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
    fun getJson(authentication: Authentication): ResponseEntity<List<Products>> {
        val list = productsService.getByHostName(HostName.ALMA)
        return ResponseEntity.ok(list)
    }

    @PostMapping("/json", produces = [APPLICATION_JSON_VALUE])
    fun produceJson(
        authentication: Authentication,
        @RequestBody @Valid products: Products
    ): ResponseEntity<Products> {
        try {
            productsService.save(products)

            return ResponseEntity.ok(
                products
            )
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().build()
        }
    }

    class Response(val message: String)
}
