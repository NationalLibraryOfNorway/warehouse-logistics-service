package no.nb.mlt.wls.product.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.junit5.MockKExtension
import no.nb.mlt.wls.EnableTestcontainers
import no.nb.mlt.wls.core.data.Environment
import no.nb.mlt.wls.core.data.HostName
import no.nb.mlt.wls.core.data.Owner
import no.nb.mlt.wls.core.data.Packaging
import no.nb.mlt.wls.order.repository.OrderRepository
import no.nb.mlt.wls.order.service.SynqOrderService
import no.nb.mlt.wls.product.payloads.ApiProductPayload
import no.nb.mlt.wls.product.repository.ProductRepository
import no.nb.mlt.wls.product.service.ProductService
import no.nb.mlt.wls.product.service.SynqProductService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.test.web.reactive.server.WebTestClient
import java.net.URI

@EnableTestcontainers
@ExtendWith(MockKExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["synq.path.base=http://localhost:9999"])
@EnableMongoRepositories("no.nb.mlt.wls")
@AutoConfigureWebTestClient
class ProductControllerTest(
    @Autowired val repository: ProductRepository
) {
    @MockkBean
    private lateinit var synqProductService: SynqProductService

    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setUp() {
        webTestClient = WebTestClient.bindToController(ProductController(ProductService(repository, synqProductService))).build()
    }

    @Test
    @WithMockUser
    fun `createProduct with valid payload creates product`() {
        every {
            synqProductService.createProduct(any())
        } returns ResponseEntity.created(URI.create("")).build()

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
