package no.nb.mlt.wls.product.controller

import no.nb.mlt.wls.product.payloads.ApiProductPayload
import no.nb.mlt.wls.product.payloads.toProduct
import no.nb.mlt.wls.product.payloads.toSynqPayload
import no.nb.mlt.wls.product.service.ProductService
import no.nb.mlt.wls.product.service.SynqService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1")
class ProductController(val synqService: SynqService, val productService: ProductService) {
    @PostMapping("/product")
    fun createProduct(
        @RequestBody payload: ApiProductPayload
    ) {
        val product = payload.toProduct()
        productService.save(product)
        synqService.createProduct(product.toSynqPayload())
    }
}
