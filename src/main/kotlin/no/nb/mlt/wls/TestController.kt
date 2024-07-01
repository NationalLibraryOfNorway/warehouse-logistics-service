package no.nb.mlt.wls

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.product.model.ProductModel
import no.nb.mlt.wls.product.dto.ProductDTO
import no.nb.mlt.wls.product.service.ProductsService
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

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
    fun getJson(authentication: Authentication): ResponseEntity<List<ProductModel>> {
        val list = productsService.getByHostName(HostName.ALMA)
        return ResponseEntity.ok(list)
    }

    @PostMapping("/json", produces = [APPLICATION_JSON_VALUE])
    fun produceJson(
        authentication: Authentication,
        @RequestBody dto: ProductDTO
    ): ResponseEntity<ProductDTO> {
        try {
            val product =  ProductModel(
                hostName = dto.hostName,
                hostId = dto.hostId,
                category = dto.category,
                description = dto.description,
                packaging = dto.packaging,
                location = dto.location,
                quantity = dto.quantity,
                preferredEnvironment = dto.preferredEnvironment,
                owner = dto.owner,
            )
            productsService.save(product)
            return ResponseEntity.ok(dto)

        } catch (e: Exception) {
            return ResponseEntity.internalServerError().build()
        }
    }

    class Response(val message: String)
}
