package no.nb.mlt.wls.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.junit5.MockKExtension
import no.nb.mlt.wls.EnableTestcontainers
import no.nb.mlt.wls.core.data.Environment
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.product.controller.ProductController
import no.nb.mlt.wls.product.payloads.ApiProductPayload
import no.nb.mlt.wls.product.service.ProductService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.test.web.reactive.server.WebTestClient

@EnableTestcontainers
@AutoConfigureDataMongo
@ExtendWith(MockKExtension::class)
@WebFluxTest(ProductController::class)
@EnableMongoRepositories("no.nb.mlt.wls.product.repository")
class ProductControllerTest(
    @Autowired val webTestClient: WebTestClient
) {
    @MockkBean
    private lateinit var productService: ProductService

    @Test
    @WithMockUser
    fun `createProduct with valid payload creates product`() {
        every {
            productService.save(testProductPayload)
        } returns ResponseEntity(testProductPayload, HttpStatus.CREATED)

        webTestClient
            .mutateWith(csrf())
            .post()
            .uri("/product")
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testProductPayload)
            .exchange()
            .expectStatus().isCreated
    }

// /////////////////////////////////////////////////////////////////////////////
// //////////////////////////////// Test Help //////////////////////////////////
// /////////////////////////////////////////////////////////////////////////////

    private val testProductPayload =
        ApiProductPayload(
            hostName = HostName.AXIELL,
            hostId = "mlt-2048",
            productCategory = "BOOK",
            description = "Ringenes Herre samling",
            packaging = Packaging.BOX,
            location = null,
            quantity = 0.0,
            preferredEnvironment = Environment.NONE,
            owner = Owner.NB
        )
}
