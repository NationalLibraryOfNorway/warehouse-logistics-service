package no.nb.mlt.wls

import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.product.dto.PackagingDTO
import no.nb.mlt.wls.product.dto.ProductDTO
import no.nb.mlt.wls.product.model.ProductModel
import no.nb.mlt.wls.product.service.ProductService
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
        @RequestBody hostName: HostName
    ): ResponseEntity<List<ProductModel>> {
        val list = productService.getByHostName(hostName)
        return ResponseEntity.ok(list)
    }

    @PostMapping("/json", produces = [APPLICATION_JSON_VALUE])
    fun produceJson(
        authentication: Authentication,
        @RequestBody dto: ProductDTO
    ): ResponseEntity<ProductDTO> {
        try {
            val product =
                ProductModel(
                    hostName = dto.hostName,
                    hostId = dto.hostId,
                    category = dto.category,
                    description = dto.description,
                    packaging = mapPackaging(dto.packaging),
                    location = dto.location,
                    quantity = dto.quantity,
                    preferredEnvironment = dto.preferredEnvironment,
                    owner = dto.owner
                )
            productService.save(product)
            return ResponseEntity.ok(dto)
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().build()
        }
    }

    class Response(val message: String)

    // TODO - Review
    fun mapPackaging(pack: PackagingDTO): Packaging {

        val newPack = when (pack) {
            PackagingDTO.OBJ -> Packaging.CRATE
            PackagingDTO.ESK -> Packaging.BOX
            PackagingDTO.ABOX -> Packaging.ABOX
            PackagingDTO.EA -> Packaging.NONE
        }
        // TODO - Logging
        println("mapping $pack to $newPack")
        return newPack
    }
}
