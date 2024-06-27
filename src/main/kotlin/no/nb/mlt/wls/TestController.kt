package no.nb.mlt.wls

import io.wispforest.endec.format.json.GsonDeserializer
import io.wispforest.endec.format.json.GsonSerializer
import jakarta.validation.Valid
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.InnerProduct
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.core.data.PreferredEnvironment
import no.nb.mlt.wls.core.data.Products
import no.nb.mlt.wls.core.dto.ProductDTO
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
    fun testStructure(authentication: Authentication): ResponseEntity<Products> {
        return ResponseEntity.ok(
            Products(HostName.ALMA, "", InnerProduct("", "test", Packaging.NONE, ""), false, PreferredEnvironment.NONE, Owner.NB)
        )
    }

    @PostMapping("/json", produces = [APPLICATION_JSON_VALUE])
    fun produceJson(
        authentication: Authentication,
        @RequestBody @Valid product: ProductDTO
    ): ResponseEntity<Products> {
        try {
            val product1 = ProductDTO.ENDEC.encodeFully(GsonSerializer::of, product)

            val product2 = Products.ENDEC.decodeFully(GsonDeserializer::of, product1)

            productsService.save(product2)

            return ResponseEntity.ok(
                product2
            )
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().build()
        }
    }

    class Response(val message: String)
}
