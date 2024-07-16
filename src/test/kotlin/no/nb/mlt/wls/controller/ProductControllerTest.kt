package no.nb.mlt.wls.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nb.mlt.wls.EnableTestcontainers
import no.nb.mlt.wls.core.data.Environment
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.product.model.Product
import no.nb.mlt.wls.product.payloads.toApiPayload
import no.nb.mlt.wls.product.service.ProductService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

@SpringBootTest
@EnableTestcontainers
@AutoConfigureMockMvc
class ProductControllerTest() {
    @MockkBean
    lateinit var productService: ProductService

    val testProduct =
        Product(
            hostName = HostName.AXIELL,
            hostId = "mlt-2048",
            productCategory = "BOOK",
            description = "Ringenes Herre samling",
            packaging = Packaging.BOX,
            location = "SYNQ_WAREHOUSE",
            quantity = 1.0,
            preferredEnvironment = Environment.NONE,
            owner = Owner.NB
        )

    @Test
    fun saveProductTest() {
        every {
            productService.save(testProduct.toApiPayload())
        } returns ResponseEntity(testProduct.toApiPayload(), HttpStatus.OK)

        val result = productService.save(testProduct.toApiPayload())
        assertEquals(HttpStatus.OK, result.statusCode)
    }
}
