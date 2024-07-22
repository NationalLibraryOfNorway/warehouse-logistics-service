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
import no.nb.mlt.wls.product.model.Product
import no.nb.mlt.wls.product.repository.ProductRepository
import no.nb.mlt.wls.product.service.ProductService
import no.nb.mlt.wls.product.service.SynqService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.reactive.server.WebTestClient

@EnableTestcontainers
@SpringBootTest
@EnableMongoRepositories("no.nb.mlt.wls.product.repository")
@ExtendWith(MockKExtension::class)
class ProductControllerTest {
    @MockkBean
    private lateinit var synqService: SynqService

    @Autowired
    lateinit var repository: ProductRepository

    val testProduct =
        Product(
            hostName = HostName.AXIELL,
            hostId = "mlt-2048",
            productCategory = "BOOK",
            description = "Ringenes Herre samling",
            packaging = Packaging.BOX,
            location = "SYNQ_WAREHOUSE",
            quantity = 0.0,
            preferredEnvironment = Environment.NONE,
            owner = Owner.NB
        )

    @Test
    fun saveProductTest() {
        populateDb(repository)
        val webTestClient: WebTestClient =
            WebTestClient
                .bindToController(ProductController(ProductService(repository, synqService)))
                .build()
        // Assumes responses from SynQ is CREATED, as this in reality requires integration testing
        every {
            synqService.createProduct(any())
        } returns ResponseEntity(SynqService.SynqError(0, ""), HttpStatus.CREATED)

        webTestClient.post()
            .uri("/product")
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(testProduct)
            .exchange()
            .expectStatus().isCreated
    }

    fun populateDb(repository: ProductRepository) {
        repository.save(
            Product(
                hostName = HostName.AXIELL,
                hostId = "product-12346",
                productCategory = "BOOK",
                description = "Tyv etter loven",
                packaging = Packaging.NONE,
                location = "SYNQ_WAREHOUSE",
                quantity = 1.0,
                preferredEnvironment = Environment.NONE,
                owner = Owner.NB
            )
        )
    }
}
